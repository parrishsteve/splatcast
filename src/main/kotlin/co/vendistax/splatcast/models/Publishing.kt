package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PublishEventRequest(
    val schemaId: Long? = null,
    val schemaName: String? = null,
    val transformToSchemaId: Long? = null,
    val transformToSchemaName: String? = null,
    val data: JsonObject,
    val idempotencyKey: String? = null
)

@Serializable
data class PublishEventResponse(
    val eventId: String,
    val topicId: Long,
    val topicName: String,
    val publishedAt: String,
    val transformsApplied: List<String> = emptyList()
)

@Serializable
data class BatchPublishRequest(
    val events: List<PublishEventRequest>
)

@Serializable
data class BatchPublishResponse(
    val published: List<PublishEventResponse>,
    val failed: List<PublishFailure>
)

@Serializable
data class PublishFailure(
    val index: Int,
    val error: String,
    val data: JsonObject
)
