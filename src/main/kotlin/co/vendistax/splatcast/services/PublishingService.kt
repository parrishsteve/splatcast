package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.entities.SchemaEntity
import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.entities.TransformerEntity
import co.vendistax.splatcast.database.tables.Topics
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.queue.QueueBusProducer
import co.vendistax.splatcast.queue.QueueChannel
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*

class SchemaVersionRequiredException(message: String) : IllegalArgumentException(message)

class SchemaMismatchException(message: String) : IllegalArgumentException(message)

class QueuePublishException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class PublishingService(
    private val transformerService: TransformerService,
    private val queueBusProducer: QueueBusProducer,
    private val logger: Logger = LoggerFactory.getLogger<PublishingService>(),
) {

    fun publishEvent(
        appId: Long,
        topicId: Long,
        request: PublishEventRequest,
        idempotencyKey: String? = null
    ): PublishEventResponse {
        // Validate request
        val validation = validatePublishRequest(appId, topicId, request)

        // Transform if needed
        val transformResult = applyTransformation(validation, request.data)

        // Publish to queue
        val eventId = idempotencyKey?.let { "evt_$it" } ?: "evt_${UUID.randomUUID()}"
        val publishedAt = OffsetDateTime.now()

        publishToQueue(
            appId = appId,
            topicId = topicId,
            eventId = eventId,
            data = transformResult.data
        )

        logger.info {
            "Published event: id=$eventId, topic=$topicId, transforms=${transformResult.transformsApplied}"
        }

        return PublishEventResponse(
            eventId = eventId,
            topicId = topicId,
            publishedAt = publishedAt.toString(),
            transformsApplied = transformResult.transformsApplied
        )
    }

    suspend fun batchPublishAsync(
        appId: Long,
        topicId: Long,
        request: BatchPublishRequest
    ): BatchPublishResponse {
        val results = coroutineScope {
            request.events.mapIndexed { index, eventRequest ->
                async {
                    index to runCatching {
                        publishEvent(
                            appId = appId,
                            topicId = topicId,
                            request = eventRequest,
                            idempotencyKey = eventRequest.idempotencyKey
                        )
                    }
                }
            }.awaitAll()
        }

        val published = mutableListOf<PublishEventResponse>()
        val failed = mutableListOf<PublishFailure>()

        results.forEach { (index, result) ->
            result
                .onSuccess { published.add(it) }
                .onFailure { error ->
                    failed.add(PublishFailure(
                        index = index,
                        error = error.message ?: "Unknown error",
                        data = request.events[index].data
                    ))
                }
        }

        logger.info {
            "Batch published: app=$appId, topic=$topicId, success=${published.size}, failed=${failed.size}"
        }

        return BatchPublishResponse(published, failed)
    }

    // Synchronous wrapper for backward compatibility
    fun batchPublish(appId: Long, topicId: Long, request: BatchPublishRequest): BatchPublishResponse {
        return runBlocking {
            batchPublishAsync(appId, topicId, request)
        }
    }

    private fun validatePublishRequest(
        appId: Long,
        topicId: Long,
        request: PublishEventRequest
    ): ValidationData = transaction {
        // Find topic
        val topic = TopicEntity.find {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }.firstOrNull()
            ?: throw TransformerNotFoundException("Topic not found: appId=$appId, topicId=$topicId")

        // Validate schema version is provided when topic has default schema
        if (topic.defaultSchemaId != null && request.schemaId <= 0L) {
            throw SchemaVersionRequiredException(
                "Schema version required for topic with default schema"
            )
        }

        // Determine target schema and find transformer if needed
        val targetSchemaId = request.transformToSchemaId ?: request.schemaId
        var transformer: TransformerEntity? = null

        if (request.schemaId > 0 &&
            request.transformToSchemaId != null &&
            request.transformToSchemaId != request.schemaId) {

            transformer = findTransformer(
                appId = appId,
                topicId = topicId,
                fromSchemaId = request.schemaId,
                toSchemaId = request.transformToSchemaId
            ) ?: throw TransformerNotFoundException(
                "No transformer found: ${request.schemaId} -> ${request.transformToSchemaId}"
            )
        }

        // Validate against topic's default schema if it exists
        if (topic.defaultSchemaId != null) {
            val defaultSchema = SchemaEntity.findById(topic.defaultSchemaId!!)
            if (defaultSchema?.id?.value != targetSchemaId) {
                throw SchemaMismatchException(
                    "Schema mismatch: topic expects version ${defaultSchema?.id?.value}, got $targetSchemaId"
                )
            }
        }

        ValidationData(
            topic = topic,
            transformer = transformer,
            targetSchemaId = targetSchemaId
        )
    }

    private fun findTransformer(
        appId: Long,
        topicId: Long,
        fromSchemaId: Long,
        toSchemaId: Long
    ): TransformerEntity? {
        val transformerData = transformerService.getTransformers(
            appId = appId,
            topicId = topicId
        ).find {
            it.fromSchemaId == fromSchemaId && it.toSchemaId == toSchemaId
        }

        return transformerData?.let { TransformerEntity.findById(it.id) }
    }

    private fun applyTransformation(
        validation: ValidationData,
        data: JsonObject
    ): TransformResult {
        return if (validation.transformer != null) {
            val transformed = transformerService.executeTransform(
                transformer = validation.transformer,
                inputData = data
            )
            TransformResult(
                data = transformed.transformedData,
                transformsApplied = listOf(transformed.transformerId.toString())
            )
        } else {
            TransformResult(data = data, transformsApplied = emptyList())
        }
    }

    private fun publishToQueue(
        appId: Long,
        topicId: Long,
        eventId: String,
        data: Any
    ) {
        try {
            val jsonData = data.toString()
            queueBusProducer.send(QueueChannel(appId.toString(), topicId.toString()), jsonData)
            logger.debug { "Published event $eventId to queue: app=$appId, topic=$topicId" }
        } catch (e: Exception) {
            throw QueuePublishException("Failed to publish to queue: ${e.message}", e)
        }
    }

    private data class ValidationData(
        val topic: TopicEntity,
        val transformer: TransformerEntity?,
        val targetSchemaId: Long
    )

    private data class TransformResult(
        val data: Any,
        val transformsApplied: List<String>
    )
}

