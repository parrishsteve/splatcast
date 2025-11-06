package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.entities.AppEntity
import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.database.entities.SchemaEntity
import co.vendistax.splatcast.database.tables.SchemaStatus
import co.vendistax.splatcast.database.tables.Schemas
import co.vendistax.splatcast.database.tables.Apps
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.dao.id.EntityID
import java.time.format.DateTimeFormatter

class SchemaNotFoundException(message: String) : NoSuchElementException(message)

class InvalidSchemaException(message: String) : IllegalArgumentException(message)

class SchemaStatusTransitionException(message: String) : IllegalStateException(message)

class SchemaService(
    private val logger: Logger = LoggerFactory.getLogger<SchemaService>(),
) {
    companion object {
        private val VALID_STATUS_TRANSITIONS = mapOf(
            SchemaStatus.DRAFT to setOf(SchemaStatus.ACTIVE, SchemaStatus.DEPRECATED),
            SchemaStatus.ACTIVE to setOf(SchemaStatus.DEPRECATED),
            SchemaStatus.DEPRECATED to emptySet()
        )
    }

    fun createSchema(
        appId: Long,
        request: CreateSchemaRequest
    ): SchemaResponse = transaction {
        // Verify the app exists
        AppEntity.findById(appId)
            ?: throw IllegalArgumentException("App not found: appId=$appId")

        // Check for duplicate name
        val existingSchema = SchemaEntity.find {
            (Schemas.appId eq appId) and (Schemas.name eq request.name)
        }.firstOrNull()

        if (existingSchema != null) {
            throw IllegalArgumentException(
                "Schema with name '${request.name}' already exists"
            )
        }

        val status = SchemaStatus.fromString(request.status)

        // Create schema
        val schema = SchemaEntity.new {
            this.appId = EntityID(appId, Apps)
            this.name = request.name
            this.jsonSchema = request.jsonSchema
            this.status = status
        }

        logger.info {
            "Created schema: id=${schema.id.value}, name=${request.name}, status=$status"
        }

        schema.toResponse()
    }

    fun getSchemas(
        appId: Long,
        status: SchemaStatus? = null,
        limit: Int = 100,
        offset: Int = 0
    ): SchemasListResponse = transaction {
        validatePagination(limit, offset)

        // Build query
        var query = Schemas.appId eq appId
        if (status != null) {
            query = query and (Schemas.status eq status)
        }

        // Fetch with pagination
        val schemas = SchemaEntity.find { query }
            .limit(limit, offset.toLong())
            .orderBy(Schemas.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toResponse() }

        val total = SchemaEntity.find { query }.count().toInt()

        SchemasListResponse(schemas, total)
    }

    fun getSchema(
        appId: Long,
        schemaId: Long
    ): SchemaResponse = transaction {
        SchemaEntity.find {
            (Schemas.id eq schemaId) and (Schemas.appId eq appId)
        }.firstOrNull()
            ?.toResponse()
            ?: throw SchemaNotFoundException("Schema not found: id=$schemaId")
    }

    fun getSchemaByName(
        appId: Long,
        name: String
    ): SchemaResponse = transaction {
        SchemaEntity.find {
            (Schemas.appId eq appId) and (Schemas.name eq name)
        }.firstOrNull()
            ?.toResponse()
            ?: throw SchemaNotFoundException("Schema not found: name=$name")
    }

    fun updateSchema(
        appId: Long,
        schemaId: Long,
        request: UpdateSchemaRequest
    ): SchemaResponse = transaction {
        val schema = SchemaEntity.find {
            (Schemas.id eq schemaId) and (Schemas.appId eq appId)
        }.firstOrNull()
            ?: throw SchemaNotFoundException("Schema not found: id=$schemaId")

        // Check for name conflict if name is being changed
        if (request.name != schema.name) {
            val existingSchema = SchemaEntity.find {
                (Schemas.appId eq appId) and (Schemas.name eq request.name)
            }.firstOrNull()

            if (existingSchema != null) {
                throw IllegalArgumentException(
                    "Schema with name '${request.name}' already exists"
                )
            }
        }

        // Validate status transition
        val newStatus = SchemaStatus.fromString(request.status)
        if (!isValidStatusTransition(schema.status, newStatus)) {
            throw SchemaStatusTransitionException(
                "Invalid status transition: ${schema.status} -> $newStatus"
            )
        }

        val oldName = schema.name
        val oldStatus = schema.status

        // Update all fields
        schema.name = request.name
        schema.jsonSchema = request.jsonSchema
        schema.status = newStatus

        logger.info {
            "Updated schema: id=$schemaId, name=$oldName -> ${request.name}, status=$oldStatus -> $newStatus"
        }

        schema.toResponse()
    }

    fun deleteSchema(
        appId: Long,
        schemaId: Long
    ): Unit = transaction {
        val schema = SchemaEntity.find {
            (Schemas.id eq schemaId) and (Schemas.appId eq appId)
        }.firstOrNull()
            ?: throw SchemaNotFoundException("Schema not found: id=$schemaId")

        // Only allow deletion of draft schemas
        if (schema.status != SchemaStatus.DRAFT) {
            throw IllegalStateException(
                "Can only delete draft schemas. Current status: ${schema.status}"
            )
        }

        val name = schema.name
        schema.delete()

        logger.info { "Deleted schema: id=$schemaId, name=$name" }
    }

    private fun validatePagination(limit: Int, offset: Int) {
        require(limit > 0) { "Limit must be greater than 0" }
        require(limit <= 100) { "Limit cannot exceed 100" }
        require(offset >= 0) { "Offset must be non-negative" }
    }

    private fun isValidStatusTransition(from: SchemaStatus, to: SchemaStatus): Boolean {
        if (from == to) return true
        return VALID_STATUS_TRANSITIONS[from]?.contains(to) ?: false
    }

    private fun SchemaEntity.toResponse(): SchemaResponse = SchemaResponse(
        id = this.id.value,
        appId = this.appId.value,
        name = this.name,
        jsonSchema = this.jsonSchema,
        status = this.status.toString(),
        createdAt = this.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    )
}
