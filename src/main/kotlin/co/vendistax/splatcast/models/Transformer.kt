package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TransformerResponse(
    val id: String,
    val appId: String,
    val topicId: String,
    val fromSchema: String?,
    val toSchema: String,
    val lang: String,
    val code: String,
    val codeHash: String,
    val timeoutMs: Int,
    val enabled: Boolean,
    val createdBy: String?,
    val createdAt: String
)

@Serializable
data class CreateTransformerRequest(
    val fromSchema: String? = null, // NULL = any schema
    val toSchema: String,
    val code: String,
    val timeoutMs: Int = 50,
    val enabled: Boolean = true,
    val createdBy: String? = null
)

@Serializable
data class UpdateTransformerRequest(
    val code: String? = null,
    val timeoutMs: Int? = null,
    val enabled: Boolean? = null
)

@Serializable
data class TransformerTestRequest(
    val inputJson: JsonObject,
    val expectJson: JsonObject
)

@Serializable
data class TransformerListResponse(
    val transforms: List<TransformerResponse>,
    val total: Int
)
