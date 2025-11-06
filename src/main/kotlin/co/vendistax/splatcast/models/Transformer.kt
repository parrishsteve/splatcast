package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TransformerResponse(
    val id: Long,
    val appId: Long,
    val topicId: Long,
    val topicName: String,
    val name: String,
    val fromSchemaId: Long?,
    val toSchemaId: Long,
    val fromSchemaName: String?,
    val toSchemaName: String,
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
    val name: String,
    val fromSchemaId: Long? = null, // NULL = any schema
    val toSchemaId: Long? = null, // This is actually required but they can alternatively provide toSchemaName
    val fromSchemaName: String? = null, // NULL = any schema
    val toSchemaName: String? = null, //  This is actually required but they can alternatively provide toSchemaId
    val code: String,
    val timeoutMs: Int = 50,
    val enabled: Boolean = true,
    val createdBy: String? = null
)

@Serializable
data class UpdateTransformerRequest(
    val name: String,
    val fromSchemaId: Long? = null, // NULL = any schema
    val toSchemaId: Long? = null,
    val fromSchemaName: String? = null,
    val toSchemaName: String? = null,
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
