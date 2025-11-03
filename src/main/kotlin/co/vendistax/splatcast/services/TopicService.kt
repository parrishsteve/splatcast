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
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.select
import kotlin.jvm.Throws

class TopicService(
    private val schemaValidationService: SchemaValidationService,
    private val logger: Logger = LoggerFactory.getLogger<TopicService>(),
) {
    companion object {
        private const val DEFAULT_QUOTA_PER_MINUTE = 6000
        private const val DEFAULT_QUOTA_PER_DAY = 1_000_000
        private const val MAX_RETENTION_HOURS = 8760 // 1 year
    }

    @Throws (IllegalArgumentException::class, SchemaNotFoundException::class )
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

        val schemaInfo = schemaValidationService.getTopicRequestSchemaInfo(appId, request)

        // Create topic
        val topicEntity = TopicEntity.new {
            this.appId = app.id
            this.name = request.name
            this.description = request.description
            this.retentionHours = request.retentionHours
            this.defaultSchemaId = schemaInfo?.let { EntityID(it.id, Schemas) }
        }

        // Create quota
        val quotas = QuotaEntity.new {
            this.appId = app.id
            this.topicId = topicEntity.id
            this.perMinute = request.quotas.perMinute
            this.perDay = request.quotas.perDay
        }

        logger.info { "Created topic: id=${topicEntity.id.value}, app=$appId, name=${request.name}" }
        topicEntity.toResponse(schemaInfo, quotas)
    }

    // Can find by AppId and TopicId or by AppId and TopicName
    private fun find(where: () -> org.jetbrains.exposed.sql.Op<Boolean>): TopicResponse = transaction {
        Topics.join(Quotas, JoinType.LEFT, Topics.id, Quotas.topicId)
            .join(Schemas, JoinType.LEFT, Topics.defaultSchemaId, Schemas.id)
            .slice(Topics.columns + Quotas.columns + Schemas.id + Schemas.name)
            .select { where() }
            .map { row ->
                val topicEntity = TopicEntity.wrapRow(row)
                val quotaEntity = row.getOrNull(Quotas.id)?.let { QuotaEntity.wrapRow(row) }
                val schemaInfo = row.getOrNull(Schemas.id)?.let {
                    SchemaInfoResult(
                        id = row[Schemas.id].value,
                        name = row[Schemas.name]
                    )
                }
                topicEntity.toResponse(schemaInfo, quotaEntity)
            }
            .firstOrNull()
            ?: throw NoSuchElementException("Topic not found")
    }

    fun findByAppId(appId: Long): List<TopicResponse> = transaction {
        Topics.join(Quotas, JoinType.LEFT, Topics.id, Quotas.topicId)
            .join(Schemas, JoinType.LEFT, Topics.defaultSchemaId, Schemas.id)
            .slice(Topics.columns + Quotas.columns + Schemas.id + Schemas.name)
            .select { Topics.appId eq appId }
            .map { row ->
                val topicEntity = TopicEntity.wrapRow(row)
                val quotaEntity = row.getOrNull(Quotas.id)?.let { QuotaEntity.wrapRow(row) }
                val schemaInfo = row.getOrNull(Schemas.id)?.let {
                    SchemaInfoResult(
                        id = row[Schemas.id].value,
                        name = row[Schemas.name]
                    )
                }
                topicEntity.toResponse(schemaInfo, quotaEntity)
            }
    }

    fun findById(appId: Long, topicId: Long): TopicResponse = find { (Topics.id eq topicId) and (Topics.appId eq appId) }

    fun findByAppIdAndName(appId: Long, name: String): TopicResponse = find { (Topics.appId eq appId) and (Topics.name eq name) }

    fun update (
        appId: Long,
        topicName: String,
        request: UpdateTopicRequest): TopicResponse =
        update(appId, { (Topics.name eq topicName) and (Topics.appId eq appId) }, request)

    fun update (
        appId: Long,
        topicId: Long,
        request: UpdateTopicRequest): TopicResponse =
        update(appId, { (Topics.id eq topicId) and (Topics.appId eq appId) }, request)

    private fun update(
        appId: Long,
        where: () -> Op<Boolean>,
        request: UpdateTopicRequest): TopicResponse = transaction {

        val topicEntity = TopicEntity.find { where() }.firstOrNull()
            ?: throw NoSuchElementException("Topic not found check IDs")

        val topicId = topicEntity.id.value

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

        var schemaInfo: SchemaInfoResult? = null
        if (request.defaultSchemaId != null || !request.defaultSchemaName.isNullOrEmpty()) {
            // then validate it
            schemaInfo = schemaValidationService.getTopicRequestSchemaInfo(appId, request)
            if (schemaInfo != null) {
                topicEntity.defaultSchemaId = EntityID(schemaInfo.id, Schemas)
            }
        }

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
        topicEntity.toResponse(schemaInfo, quota)
    }

    private fun patch(
        appId: Long,
        where: () -> Op<Boolean>,
        request: PatchTopicRequest): TopicResponse = transaction {
        val topicEntity = TopicEntity.find { where() }.firstOrNull()
            ?: throw NoSuchElementException("Topic not found check IDs")

        // Validate if changing default schema
        val schemaInfo = schemaValidationService.getTopicRequestSchemaInfo(appId, request)
            ?: throw IllegalArgumentException("No schema exists specified for topic")
        topicEntity.toResponse(schemaInfo)
    }

    fun patch(
        appId: Long,
        topicId: Long,
        request: PatchTopicRequest): TopicResponse =
        patch(appId, { (Topics.id eq topicId) and (Topics.appId eq appId) }, request)


    fun patch (
        appId: Long,
        topicName: String,
        request: PatchTopicRequest): TopicResponse =
        patch(appId, { (Topics.name eq topicName) and (Topics.appId eq appId) }, request)

    private fun delete(
        appId: Long,
        where: () -> Op<Boolean>) = transaction {
        val topicEntity = TopicEntity.find { where() }.firstOrNull()
            ?: throw NoSuchElementException("Topic not found: app=$appId")
        topicEntity.delete()
        logger.info { "Deleted topic: id=${topicEntity.id.value}, name=${topicEntity.name} app=$appId" }
    }

    fun delete(appId: Long, topicId: Long) = delete(appId, where = { (Topics.id eq topicId) and (Topics.appId eq appId) })

    fun delete(appId: Long, topicName: String) = delete(appId, where = { (Topics.name eq topicName) and (Topics.appId eq appId) })

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

    private fun TopicEntity.toResponse(schemaInfo: SchemaInfoResult?, quotaEntity: QuotaEntity? = null): TopicResponse {
        return TopicResponse(
            id = this.id.value,
            appId = this.appId.value,
            name = this.name,
            description = this.description,
            retentionHours = this.retentionHours,
            defaultSchemaId = schemaInfo?.id,
            defaultSchemaName = schemaInfo?.name,
            quotas = QuotaSettings(
                perMinute = quotaEntity?.perMinute ?: DEFAULT_QUOTA_PER_MINUTE,
                perDay = quotaEntity?.perDay ?: DEFAULT_QUOTA_PER_DAY
            ),
            createdAt = this.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            updatedAt = this.updatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
    }
}
