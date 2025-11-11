package co.vendistax.splatcast.services.facilities

import co.vendistax.splatcast.database.tables.Schemas
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.CreateTopicRequest
import co.vendistax.splatcast.models.CreateTransformerRequest
import co.vendistax.splatcast.models.PatchTopicRequest
import co.vendistax.splatcast.models.PublishEventRequest
import co.vendistax.splatcast.models.UpdateTopicRequest
import co.vendistax.splatcast.models.UpdateTransformerRequest
import co.vendistax.splatcast.services.SchemaNotFoundException
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

data class SchemaInfoResult(
    val id: Long,
    val name: String,
)

data class TransformerSchemaInfoResult(
    val from: SchemaInfoResult?,
    val to: SchemaInfoResult,
)

class SchemaValidation(
    val logger: Logger = LoggerFactory.getLogger("SchemaValidationService"),
) {
    @Throws(IllegalArgumentException::class, SchemaNotFoundException::class)
    private fun verifyAndGetSchemaInfo(appId: Long, targetSchemaId: Long?, targetSchemaName: String?): SchemaInfoResult = transaction {
        val schemaById = targetSchemaId?.let {
            Schemas.slice(Schemas.id, Schemas.name)
                .select { (Schemas.id eq it) and (Schemas.appId eq appId) }
                .singleOrNull()
                ?.let { row ->
                    SchemaInfoResult(row[Schemas.id].value, row[Schemas.name])
                } ?: throw SchemaNotFoundException("Schema not found: id=$it")
        }

        val schemaByName = targetSchemaName?.takeIf { it.isNotEmpty() }?.let {
            Schemas.slice(Schemas.id, Schemas.name)
                .select { (Schemas.appId eq appId) and (Schemas.name eq it) }
                .singleOrNull()
                ?.let { row ->
                    SchemaInfoResult(row[Schemas.id].value, row[Schemas.name])
                } ?: throw SchemaNotFoundException("Schema not found: name=$it")
        }

        when {
            schemaById != null && schemaByName != null -> {
                require(schemaById.id == schemaByName.id) {
                    "Default schema ID and name refer to different schemas"
                }
                schemaById
            }
            schemaById != null -> schemaById
            schemaByName != null -> schemaByName
            else -> throw SchemaNotFoundException("No schema exists specified for topic")
        }
    }

    private fun getTransformerRequestSchemaInfo(
        appId: Long,
        fromSchemaId: Long?,
        fromSchemaName: String?,
        toSchemaId: Long?,
        toSchemaName: String?
    ): TransformerSchemaInfoResult {
        if (fromSchemaId == null && fromSchemaName.isNullOrEmpty()) {
            return TransformerSchemaInfoResult(
                from = null,
                to = verifyAndGetSchemaInfo(appId, toSchemaId, toSchemaName)
            )
        }
        return TransformerSchemaInfoResult(
            from = verifyAndGetSchemaInfo(appId, fromSchemaId, fromSchemaName),
            to = verifyAndGetSchemaInfo(appId, toSchemaId, toSchemaName)
        )
    }

    fun getTransformerRequestSchemaInfo(appId: Long, request: PublishEventRequest): Pair<Long, Long?> {
        val schemaId = request.schemaId ?: verifyAndGetSchemaInfo(appId, null, request.schemaName).id

        val targetSchemaId = request.transformToSchemaId
            ?: request.transformToSchemaName?.takeIf { it.isNotEmpty() }
                ?.let { verifyAndGetSchemaInfo(appId, null, it).id }

        return Pair(schemaId, targetSchemaId)
    }


    fun getTransformerRequestSchemaInfo(
        appId: Long,
        request: UpdateTransformerRequest
    ) = getTransformerRequestSchemaInfo(appId, request.fromSchemaId, request.fromSchemaName, request.toSchemaId, request.toSchemaName)

    fun getTransformerRequestSchemaInfo(
        appId: Long,
        request: CreateTransformerRequest
    ) = getTransformerRequestSchemaInfo(appId, request.fromSchemaId, request.fromSchemaName, request.toSchemaId, request.toSchemaName)

    fun getTopicRequestSchemaInfo(appId: Long, request: CreateTopicRequest): SchemaInfoResult? {
        if (request.defaultSchemaId == null && request.defaultSchemaName.isNullOrEmpty()) {
            return null
        }
        return verifyAndGetSchemaInfo(appId, request.defaultSchemaId, request.defaultSchemaName)
    }

    fun getTopicRequestSchemaInfo(appId: Long, request: UpdateTopicRequest): SchemaInfoResult? {
        if (request.defaultSchemaId == null && request.defaultSchemaName.isNullOrEmpty()) {
            return null
        }
        return verifyAndGetSchemaInfo(appId, request.defaultSchemaId, request.defaultSchemaName)
    }
    fun getTopicRequestSchemaInfo(appId: Long, request: PatchTopicRequest): SchemaInfoResult? {
        if (request.defaultSchemaId == null && request.defaultSchemaName.isNullOrEmpty()) {
            return null
        }
        return verifyAndGetSchemaInfo(appId, request.defaultSchemaId, request.defaultSchemaName)
    }
}