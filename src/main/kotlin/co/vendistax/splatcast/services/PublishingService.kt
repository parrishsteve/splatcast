package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.entities.TransformerEntity
import co.vendistax.splatcast.database.tables.Topics
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.BatchPublishRequest
import co.vendistax.splatcast.models.BatchPublishResponse
import co.vendistax.splatcast.models.PublishEventRequest
import co.vendistax.splatcast.models.PublishEventResponse
import co.vendistax.splatcast.models.PublishFailure
import co.vendistax.splatcast.queue.QueueBus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*

class PublishingService(
    private val transformerService: TransformerService,
    private val queueBus: QueueBus,
    private val logger: Logger = LoggerFactory.getLogger<PublishingService>(),
) {

    fun publishEvent(appId: String, topicId: String, request: PublishEventRequest): Result<PublishEventResponse> {
        // Perform database operations in transaction
        val validationResult = transaction {
            try {
                val topic = TopicEntity.find {
                    Topics.id eq topicId and (Topics.appId eq appId)
                }.firstOrNull()
                    ?: return@transaction Result.failure<ValidationData>(Exception("Topic not found"))

                var targetSchema: String? = null
                var transformer: TransformerEntity? = null

                if (request.transformToSchema != null && request.transformToSchema != request.schemaVersion) {
                    val result = transformerService.getTransforms(appId = appId, topicId = topicId)
                        .getOrNull()?.find { it.toSchema == request.transformToSchema && it.fromSchema == request.schemaVersion }

                    if (result != null) {
                        transformer = TransformerEntity.findById(result.id)
                    }
                    if (transformer == null) {
                        return@transaction Result.failure(Exception("Requested transformer does not exist"))
                    }
                    targetSchema = request.transformToSchema
                } else {
                    targetSchema = request.schemaVersion
                }

                if (topic.defaultSchemaId != null && targetSchema != topic.defaultSchemaId) {
                    return@transaction Result.failure(Exception("Invalid schema: topic expects ${topic.defaultSchemaId} but received $targetSchema"))
                }

                Result.success(ValidationData(topic, transformer, targetSchema))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (validationResult.isFailure) {
            return Result.failure(validationResult.exceptionOrNull()!!)
        }

        val validation = validationResult.getOrThrow()
        val eventId = "evt_${UUID.randomUUID()}"
        val publishedAt = OffsetDateTime.now()
        var eventData = request.data
        val transformsApplied = mutableListOf<String>()

        // Apply transforms if needed
        if (validation.transformer != null) {
            val transformed = transformerService.executeTransform(
                transformer = validation.transformer,
                inputData = eventData
            ).getOrElse { error ->
                return Result.failure(Exception("Transform failed: ${error.message}"))
            }

            eventData = transformed.transformedData
            transformsApplied.add(transformed.transformId)
        }

        // Publish to queue (I/O operation outside transaction)
        val jsonEventData = eventData.toString()
        queueBus.publish(appId, topicId, jsonEventData)

        val response = PublishEventResponse(
            eventId = eventId,
            topicId = topicId,
            publishedAt = publishedAt.toString(),
            transformsApplied = transformsApplied
        )

        return Result.success(response)
    }

    fun batchPublish(appId: String, topicId: String, request: BatchPublishRequest): Result<BatchPublishResponse> {
        try {
            val published = mutableListOf<PublishEventResponse>()
            val failed = mutableListOf<PublishFailure>()

            request.events.forEachIndexed { index, eventRequest ->
                publishEvent(appId, topicId, eventRequest)
                    .onSuccess { response -> published.add(response) }
                    .onFailure { error ->
                        failed.add(PublishFailure(
                            index = index,
                            error = error.message ?: "Unknown error",
                            data = eventRequest.data
                        ))
                    }
            }

            return Result.success(BatchPublishResponse(published, failed))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private data class ValidationData(
        val topic: TopicEntity,
        val transformer: TransformerEntity?,
        val targetSchema: String
    )
}
