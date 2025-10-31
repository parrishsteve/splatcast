package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SchemaResponse(
    val id: Long,
    val appId: Long,
    val name: String,
    val jsonSchema: JsonObject,
    val status: String,
    val createdAt: String
)

@Serializable
data class CreateSchemaRequest(
    val name: String,
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

