package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.entities.AppEntity
import co.vendistax.splatcast.database.entities.QuotaEntity
import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.tables.Quotas
import co.vendistax.splatcast.database.tables.Schemas
import co.vendistax.splatcast.database.tables.Topics
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import org.jetbrains.exposed.sql.transactions.transaction
import co.vendistax.splatcast.models.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.exposed.dao.id.EntityID

class TopicService(
    private val logger: Logger = LoggerFactory.getLogger<TopicService>(),
) {
    companion object {
        private const val DEFAULT_QUOTA_PER_MINUTE = 6000
        private const val DEFAULT_QUOTA_PER_DAY = 1_000_000
        private const val MAX_RETENTION_HOURS = 8760 // 1 year
    }

    fun create(appId: Long, request: CreateTopicRequest): TopicResponse = transaction {
        // Validate app exists
        val app = AppEntity.findById(appId)
            ?: throw NoSuchElementException("App not found: $appId")

        // Validate request
        validateTopicRequest(request)

        // Check name uniqueness
        if (isTopicNameTaken(appId, request.name)) {
            throw IllegalArgumentException("Topic name already exists: ${request.name}")
        }

        // Create topic
        val topicEntity = TopicEntity.new {
            this.appId = app.id
            this.name = request.name
            this.description = request.description
            this.retentionHours = request.retentionHours
            this.defaultSchemaId = request.defaultSchemaId?.let { EntityID(it, Schemas) }
        }

        // Create quota
        val quotas = QuotaEntity.new {
            this.appId = app.id
            this.topicId = topicEntity.id
            this.perMinute = request.quotas.perMinute
            this.perDay = request.quotas.perDay
        }

        logger.info { "Created topic: id=${topicEntity.id.value}, app=$appId, name=${request.name}" }
        topicEntity.toResponse(quotas)
    }

    fun findByAppId(appId: Long): List<TopicResponse> = transaction {
        val topics = TopicEntity.find { Topics.appId eq appId }.toList()
        val topicIds = topics.map { it.id.value }

        val quotaMap = QuotaEntity.find { Quotas.topicId inList topicIds }
            .associateBy { it.topicId?.value }

        topics.map { it.toResponse(quotaMap[it.id.value]) }
    }

    fun findById(appId: Long, topicId: Long): TopicResponse = transaction {
        val topicEntity = TopicEntity.find {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }.firstOrNull()
            ?: throw NoSuchElementException("Topic not found: id=$topicId, app=$appId")

        val quota = QuotaEntity.find { Quotas.topicId eq topicId }.firstOrNull()
        topicEntity.toResponse(quota)
    }

    fun findByAppIdAndName(appId: Long, name: String): List<TopicResponse> = transaction {
        val topics = TopicEntity.find {
            (Topics.appId eq appId) and (Topics.name eq name)
        }.toList()

        val topicIds = topics.map { it.id.value }
        val quotaMap = QuotaEntity.find { Quotas.topicId inList topicIds }
            .associateBy { it.topicId?.value }

        topics.map { it.toResponse(quotaMap[it.id.value]) }
    }

    fun update(appId: Long, topicId: Long, request: UpdateTopicRequest): TopicResponse = transaction {
        val topicEntity = TopicEntity.find {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }.firstOrNull()
            ?: throw NoSuchElementException("Topic not found: id=$topicId, app=$appId")

        // Validate if changing name
        request.name?.let { newName ->
            if (newName != topicEntity.name && isTopicNameTaken(appId, newName, excludeTopicId = topicId)) {
                throw IllegalArgumentException("Topic name already exists: $newName")
            }
            topicEntity.name = newName
        }

        // Validate retention hours
        request.retentionHours?.let { hours ->
            if (hours < 1 || hours > MAX_RETENTION_HOURS) {
                throw IllegalArgumentException("Retention hours must be between 1 and $MAX_RETENTION_HOURS")
            }
            topicEntity.retentionHours = hours
        }

        request.description?.let { topicEntity.description = it }
        request.defaultSchemaId?.let { topicEntity.defaultSchemaId = EntityID(it, Schemas) }
        topicEntity.updatedAt = OffsetDateTime.now()

        // Update quotas
        request.quotas?.let { quotaSettings ->
            validateQuotas(quotaSettings)

            val quotaEntity = QuotaEntity.find { Quotas.topicId eq topicId }.firstOrNull()
                ?: throw IllegalStateException("Quota not found for topic: $topicId")

            quotaEntity.perMinute = quotaSettings.perMinute
            quotaEntity.perDay = quotaSettings.perDay
        }

        val quota = QuotaEntity.find { Quotas.topicId eq topicId }.firstOrNull()
        logger.info { "Updated topic: id=$topicId, app=$appId" }
        topicEntity.toResponse(quota)
    }

    fun updateDefaultSchema(appId: Long, topicId: Long, defaultSchemaId: Long): TopicResponse = transaction {
        val topicEntity = TopicEntity.find {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }.firstOrNull()
            ?: throw NoSuchElementException("Topic not found: appId=$appId, topicId=$topicId")

        topicEntity.defaultSchemaId = EntityID(defaultSchemaId, Schemas)
        topicEntity.updatedAt = OffsetDateTime.now()

        logger.info { "Updated default schema for topic: topicId=$topicId, schemaId=$defaultSchemaId" }

        val quota = QuotaEntity.find { Quotas.topicId eq topicId }.firstOrNull()
        topicEntity.toResponse(quota)
    }

    fun delete(appId: Long, topicId: Long) = transaction {
        val topicEntity = TopicEntity.find {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }.firstOrNull()
            ?: throw NoSuchElementException("Topic not found: id=$topicId, app=$appId")

        QuotaEntity.find { Quotas.topicId eq topicId }.forEach { it.delete() }
        topicEntity.delete()
        logger.info { "Deleted topic: id=$topicId, app=$appId" }
    }

    private fun isTopicNameTaken(appId: Long, name: String, excludeTopicId: Long? = null): Boolean {
        val query = (Topics.appId eq appId) and (Topics.name eq name)
        val topics = TopicEntity.find(query)

        return if (excludeTopicId != null) {
            topics.any { it.id.value != excludeTopicId }
        } else {
            !topics.empty()
        }
    }

    private fun validateTopicRequest(request: CreateTopicRequest) {
        require(request.name.isNotBlank()) { "Topic name cannot be blank" }
        require(request.retentionHours in 1..MAX_RETENTION_HOURS) {
            "Retention hours must be between 1 and $MAX_RETENTION_HOURS"
        }
        validateQuotas(request.quotas)
    }

    private fun validateQuotas(quotas: QuotaSettings) {
        require(quotas.perMinute > 0) { "Quota per minute must be positive" }
        require(quotas.perDay > 0) { "Quota per day must be positive" }
        require(quotas.perMinute * 60 * 24 >= quotas.perDay) {
            "Daily quota cannot exceed per-minute quota * minutes in day"
        }
    }

    private fun TopicEntity.toResponse(quotaEntity: QuotaEntity? = null): TopicResponse {
        return TopicResponse(
            id = this.id.value,
            appId = this.appId.value,
            name = this.name,
            description = this.description,
            retentionHours = this.retentionHours,
            defaultSchemaId = this.defaultSchemaId?.value,
            quotas = QuotaSettings(
                perMinute = quotaEntity?.perMinute ?: DEFAULT_QUOTA_PER_MINUTE,
                perDay = quotaEntity?.perDay ?: DEFAULT_QUOTA_PER_DAY
            ),
            createdAt = this.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            updatedAt = this.updatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
    }
}
