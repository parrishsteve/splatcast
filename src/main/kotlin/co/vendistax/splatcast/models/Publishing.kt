package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PublishEventRequest(
    val data: JsonObject,
    val schemaVersion: String,
    val transformToSchema: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class PublishEventResponse(
    val eventId: String,
    val topicId: String,
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
