package co.vendistax.splatcast.models

import co.vendistax.splatcast.validation.validateJsonSchema
import co.vendistax.splatcast.validation.validateName
import co.vendistax.splatcast.validation.validateSchemaStatus
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
) {
    fun validate(): CreateSchemaRequest {
        name.validateName("name")
        jsonSchema.validateJsonSchema()
        status.validateSchemaStatus("status")
        return this
    }
}

@Serializable
data class UpdateSchemaRequest(
    val name: String,
    val jsonSchema: JsonObject,
    val status: String = "active"
) {
    fun validate(): UpdateSchemaRequest {
        name.validateName("name")
        jsonSchema.validateJsonSchema()
        status.validateSchemaStatus("status")
        return this
    }
}

@Serializable
data class SchemasListResponse(
    val schemas: List<SchemaResponse>,
    val total: Int
)