package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SchemaResponse(
    val id: String,
    val appId: String,
    val topicId: String,
    val version: String,
    val jsonSchema: JsonObject,
    val status: String,
    val createdAt: String
)

@Serializable
data class CreateSchemaRequest(
    val version: String,
    val jsonSchema: JsonObject,
    val status: String = "active"
)

@Serializable
data class UpdateSchemaRequest(
    val status: String
)

@Serializable
data class SchemasListResponse(
    val schemas: List<SchemaResponse>,
    val total: Int
)

