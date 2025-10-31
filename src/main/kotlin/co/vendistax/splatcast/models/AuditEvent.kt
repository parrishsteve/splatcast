package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuditEventResponse(
    val id: Long,
    val actor: String,
    val action: String,
    val target: String,
    val details: JsonObject? = null,
    val at: String
)

@Serializable
data class AuditEventsListResponse(
    val events: List<AuditEventResponse>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

data class CreateAuditEventRequest(
    val actor: String,
    val action: String,
    val target: String,
    val details: JsonObject? = null
)
