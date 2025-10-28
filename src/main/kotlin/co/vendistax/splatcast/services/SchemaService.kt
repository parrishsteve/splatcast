package co.vendistax.splatcast.services

import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.database.entities.SchemaEntity
import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.tables.SchemaStatus
import co.vendistax.splatcast.database.tables.Schemas
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

class SchemaService(
    private val logger: Logger = LoggerFactory.getLogger<SchemaService>(),
) {

    fun createSchema(appId: String, topicId: String, request: CreateSchemaRequest): Result<SchemaResponse> = transaction {
        try {
            // Verify topic exists and belongs to app
            val topic = TopicEntity.find {
                co.vendistax.splatcast.database.tables.Topics.id eq topicId and
                        (co.vendistax.splatcast.database.tables.Topics.appId eq appId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Topic not found"))

            // Check if version already exists
            val existingSchema = SchemaEntity.find {
                Schemas.appId eq appId and
                        (Schemas.topicId eq topicId) and
                        (Schemas.version eq request.version)
            }.firstOrNull()

            if (existingSchema != null) {
                return@transaction Result.failure(Exception("Schema version ${request.version} already exists"))
            }

            val schemaId = "schema_${System.currentTimeMillis()}"
            val status = SchemaStatus.fromString(request.status)

            val schema = SchemaEntity.new(schemaId) {
                this.appId = appId
                this.topicId = topicId
                this.version = request.version
                this.jsonSchema = request.jsonSchema
                this.status = status
            }

            Result.success(schema.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSchemas(appId: String, topicId: String): Result<SchemasListResponse> = transaction {
        try {
            val schemas = SchemaEntity.find {
                Schemas.appId eq appId and (Schemas.topicId eq topicId)
            }.map { it.toResponse() }

            Result.success(SchemasListResponse(schemas, schemas.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSchema(appId: String, topicId: String, schemaId: String): Result<SchemaResponse> = transaction {
        try {
            val schema = SchemaEntity.find {
                Schemas.id eq schemaId and
                        (Schemas.appId eq appId) and
                        (Schemas.topicId eq topicId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Schema not found"))

            Result.success(schema.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateSchema(appId: String, topicId: String, schemaId: String, request: UpdateSchemaRequest): Result<SchemaResponse> = transaction {
        try {
            val schema = SchemaEntity.find {
                Schemas.id eq schemaId and
                        (Schemas.appId eq appId) and
                        (Schemas.topicId eq topicId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Schema not found"))

            schema.status = SchemaStatus.fromString(request.status)
            Result.success(schema.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun SchemaEntity.toResponse(): SchemaResponse = SchemaResponse(
        id = this.id.value,
        appId = this.appId,
        topicId = this.topicId,
        version = this.version,
        jsonSchema = this.jsonSchema,
        status = this.status.toString(),
        createdAt = this.createdAt.toString()
    )
}
