package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.entities.AppEntity
import co.vendistax.splatcast.database.entities.QuotaEntity
import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.tables.Quotas
import co.vendistax.splatcast.database.tables.Topics
import org.jetbrains.exposed.sql.transactions.transaction
import co.vendistax.splatcast.models.*
import org.jetbrains.exposed.sql.and
import java.time.format.DateTimeFormatter

class TopicService {

    fun create(appId: String, request: CreateTopicRequest): TopicResponse = transaction {
        // Check if app exists
        AppEntity.findById(appId) ?: throw NoSuchElementException("App with id '$appId' not found")

        // Check if topic name already exists for this app
        val existingTopicEntity = TopicEntity.find {
            Topics.appId eq appId and (Topics.name eq request.name)
        }.firstOrNull()

        if (existingTopicEntity != null) {
            throw IllegalArgumentException("Topic with name '${request.name}' already exists for this app")
        }

        // Generate IDs
        val topicId = "topic_${generateId()}"
        val quotaId = "quota_${generateId()}"

        // Create topic
        val topicEntity = TopicEntity.new(topicId) {
            this.appId = appId
            this.name = request.name
            this.description = request.description
            this.retentionHours = request.retentionHours
            this.defaultSchemaId = request.defaultSchemaId
        }

        // Create quota
        QuotaEntity.new(quotaId) {
            this.appId = appId
            this.topicId = topicId
            this.perMinute = request.quotas.perMinute
            this.perDay = request.quotas.perDay
        }

        topicEntity.toResponse()
    }

    fun findByAppId(appId: String): List<TopicResponse> = transaction {
        TopicEntity.find { Topics.appId eq appId }
            .map { it.toResponse() }
    }

    fun findById(appId: String, topicId: String): TopicResponse? = transaction {
        TopicEntity.find {
            Topics.id eq topicId and (Topics.appId eq appId)
        }.firstOrNull()?.toResponse()
    }

    fun update(appId: String, topicId: String, request: UpdateTopicRequest): TopicResponse = transaction {
        val topicEntity = TopicEntity.find {
            Topics.id eq topicId and (Topics.appId eq appId)
        }.firstOrNull() ?: throw NoSuchElementException("Topic not found")

        // Update topic fields
        request.name?.let { topicEntity.name = it }
        request.description?.let { topicEntity.description = it }
        request.retentionHours?.let { topicEntity.retentionHours = it }
        request.defaultSchemaId?.let { topicEntity.defaultSchemaId = it }
        topicEntity.updatedAt = java.time.OffsetDateTime.now()

        // Update quotas if provided
        request.quotas?.let { quotaSettings ->
            val quotaEntity = QuotaEntity.find { Quotas.topicId eq topicId }.firstOrNull()
            if (quotaEntity != null) {
                quotaEntity.perMinute = quotaSettings.perMinute
                quotaEntity.perDay = quotaSettings.perDay
            }
        }

        topicEntity.toResponse()
    }

    fun delete(appId: String, topicId: String) = transaction {
        val topicEntity = TopicEntity.find {
            Topics.id eq topicId and (Topics.appId eq appId)
        }.firstOrNull() ?: throw NoSuchElementException("Topic not found")

        // Delete quota (will cascade delete due to FK)
        QuotaEntity.find { Quotas.topicId eq topicId }.forEach { it.delete() }

        topicEntity.delete()
    }

    private fun TopicEntity.toResponse(): TopicResponse {
        val topicId = this.id.value
        val quotaEntity = QuotaEntity.find { Quotas.topicId eq topicId }.firstOrNull()

        return TopicResponse(
            id = this.id.value,
            appId = this.appId,
            name = this.name,
            description = this.description,
            retentionHours = this.retentionHours,
            defaultSchemaId = this.defaultSchemaId,
            quotas = QuotaSettings(
                perMinute = quotaEntity?.perMinute ?: 6000,
                perDay = quotaEntity?.perDay ?: 1000000
            ),
            createdAt = this.createdAt.format(DateTimeFormatter.ISO_INSTANT),
            updatedAt = this.updatedAt.format(DateTimeFormatter.ISO_INSTANT)
        )
    }

    private fun generateId(): String {
        return java.util.UUID.randomUUID().toString().replace("-", "").take(12)
    }
}

