package co.vendistax.splatcast.services

import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.entities.TransformerEntity
import co.vendistax.splatcast.database.tables.Apps
import co.vendistax.splatcast.database.tables.Schemas
import co.vendistax.splatcast.database.tables.Topics
import co.vendistax.splatcast.database.tables.TransformLang
import co.vendistax.splatcast.database.tables.Transformers
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.services.facilities.SchemaValidation
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import kotlin.Boolean


class InvalidTransformCodeException(message: String) : IllegalArgumentException(message)

class DuplicateTransformerException(message: String) : IllegalStateException(message)

class TransformerExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class TransformerNotFoundException(message: String) : NoSuchElementException(message)

class TransformerService(
    private val jsRuntime: JavaScriptRuntimeService,
    private val schemaValidation: SchemaValidation,
    private val logger: Logger = LoggerFactory.getLogger<TransformerService>(),
) {


    fun createTransform(
        appId: Long,
        topicName: String,
        request: CreateTransformerRequest
    ): TransformerResponse =
        createTransformer(appId, where = {
            (Topics.name eq topicName) and (Topics.appId eq appId)
        }, request)

    fun createTransform(
        appId: Long,
        topicId: Long,
        request: CreateTransformerRequest
    ): TransformerResponse =
        createTransformer(appId, where = {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }, request)

    private fun createTransformer(
        appId: Long,
        where: () -> org.jetbrains.exposed.sql.Op<Boolean>,
        request: CreateTransformerRequest
    ): TransformerResponse = transaction {
        // Verify topic exists and belongs to app
        val topic = TopicEntity.find { where() }.firstOrNull()
            ?: throw TransformerNotFoundException("Topic not found, check provided topic identifiers use a valid ID or name")

        //Verify that the name is available for this transformer.  TODO should this be per-topic?
        if (TransformerEntity.find {
            (Transformers.appId eq appId) and
                    (Transformers.name eq request.name)
        }.firstOrNull() != null) {
            throw DuplicateTransformerException("Transformer with name '${request.name}' already exists for app $appId")
        }

        // Convert and validate schema IDs from provided IDs or names
        val schemaInfo = schemaValidation.getTransformerRequestSchemaInfo(appId, request)

        // Validate JavaScript syntax before saving
        jsRuntime.validateTransformSyntax(request.code)

        val codeHash = generateCodeHash(request.code)

        // Check for duplicate active transform with same hash
        val existingTransform = TransformerEntity.find {
            (Transformers.appId eq appId) and
                    (Transformers.name eq request.name) and
                    (Transformers.topicId eq topic.id) and
                    (Transformers.fromSchemaId eq schemaInfo.from?.id) and
                    (Transformers.toSchemaId eq schemaInfo.to.id) and
                    (Transformers.codeHash eq codeHash) and
                    (Transformers.enabled eq true)
        }.firstOrNull()

        if (existingTransform != null) {
            throw DuplicateTransformerException(
                "Active transformer already exists: ${schemaInfo.from} -> ${schemaInfo.to}"
            )
        }

        val transform = TransformerEntity.new {
            this.appId = EntityID(appId, Apps)
            this.topicId = EntityID(topic.id.value, Topics)
            this.name = request.name
            this.fromSchemaId = schemaInfo.from?.let { EntityID(it.id, Schemas) }
            this.toSchemaId = EntityID(schemaInfo.to.id, Schemas)
            this.lang = TransformLang.JS
            this.code = request.code
            this.codeHash = codeHash
            this.timeoutMs = request.timeoutMs
            this.enabled = request.enabled
            this.createdBy = request.createdBy
        }

        logger.info {
            "Created transform: id=${transform.id.value}, ${request.fromSchemaId} -> ${request.toSchemaId}"
        }

        transform.toResponse(fromSchemaName = schemaInfo.from?.name, toSchemaName = schemaInfo.to.name, topicName = topic.name)
    }

    fun findTransformerBySchemas(
        appId: Long,
        topicId: Long,
        fromSchemaId: Long?, // pass null when transformer.fromSchemaId should be NULL
        toSchemaId: Long
    ): TransformerEntity? = transaction {
        var condition: Op<Boolean> =
            (Transformers.appId eq appId) and
                    (Transformers.topicId eq topicId) and
                    (Transformers.toSchemaId eq EntityID(toSchemaId, Schemas))

        condition = if (fromSchemaId != null && fromSchemaId > 0L) {
            condition and (Transformers.fromSchemaId eq EntityID(fromSchemaId, Schemas))
        } else {
            condition and Transformers.fromSchemaId.isNull()
        }

        TransformerEntity.find { condition }.firstOrNull()
    }

    fun executeTransform(
        transformer: TransformerEntity,
        inputData: JsonObject
    ): TransformerExecutionResult {
        try {
            val transformedData = jsRuntime.executeTransform(
                transformer.code,
                inputData,
                transformer.timeoutMs
            ).getOrThrow()

            return TransformerExecutionResult(
                transformerId = transformer.id.value,
                fromSchemaId = transformer.fromSchemaId?.value,
                toSchemaId = transformer.toSchemaId.value,
                originalData = inputData,
                transformedData = transformedData
            )
        } catch (e: Exception) {
            throw TransformerExecutionException(
                "Transform execution failed: ${transformer.id.value}",
                e
            )
        }
    }

    private fun getTransformers(
        topicEntity: TopicEntity
    ): List<TransformerResponse> {
        val toSchema = Schemas.alias("toSchema")
        val fromSchema = Schemas.alias("fromSchema")

        return Transformers
            .join(toSchema, JoinType.INNER, Transformers.toSchemaId, toSchema[Schemas.id])
            .join(fromSchema, JoinType.LEFT, Transformers.fromSchemaId, fromSchema[Schemas.id])
            .slice(
                Transformers.columns.toList() +
                        listOf(
                            toSchema[Schemas.id],
                            toSchema[Schemas.name],
                            fromSchema[Schemas.id],
                            fromSchema[Schemas.name]
                        )
            )
            .select { (Transformers.appId eq topicEntity.appId) and (Transformers.topicId eq topicEntity.id.value) }
            .map { row ->
                val transformer = TransformerEntity.wrapRow(row)
                val fromSchemaName = row.getOrNull(fromSchema[Schemas.name])
                val toSchemaName = row[toSchema[Schemas.name]]
                transformer.toResponse(fromSchemaName = fromSchemaName, toSchemaName = toSchemaName, topicName = topicEntity.name)
            }
    }

    fun getTransformers(
        appId: Long,
        topicName: String
    ): List<TransformerResponse> = transaction {
        val topicEntity = TopicEntity.find { (Topics.appId eq appId) and (Topics.name eq topicName ) }.firstOrNull() ?:
            throw TransformerNotFoundException("Topic not found for appId=$appId and topicName=$topicName")
        getTransformers(topicEntity)
    }

    fun getTransformers(
        appId: Long,
        topicId: Long
    ): List<TransformerResponse> = transaction {
        val topicEntity = TopicEntity.find { (Topics.appId eq appId) and (Topics.id eq topicId ) }.firstOrNull() ?:
            throw TransformerNotFoundException("Topic not found for appId=$appId and topicId=$topicId")
        getTransformers(topicEntity)
    }

    private fun getTransformerEntityFromTopicEntity(
        topicEntity: TopicEntity,
        toSchemaId: Long
    ): TransformerEntity? {
        require(topicEntity.defaultSchemaId != null) {
            "Topic ${topicEntity.id.value} does not have a default schema defined!! Cannot find transformer."
        }

        val baseCondition = (Transformers.appId eq topicEntity.appId.value) and
                (Transformers.topicId eq topicEntity.id.value) and
                (Transformers.toSchemaId eq EntityID(toSchemaId, Schemas)) and
                (Transformers.enabled eq true)

        val schemaCondition = Transformers.fromSchemaId.isNull() or
                (Transformers.fromSchemaId eq topicEntity.defaultSchemaId)

        return TransformerEntity.find { baseCondition and schemaCondition }.firstOrNull()
    }

    fun getTransformerEntityFromTopic(
        topicEntity: TopicEntity,
        toSchemaId: Long
    ): TransformerEntity = transaction {
        getTransformerEntityFromTopicEntity(topicEntity, toSchemaId) ?: throw TransformerNotFoundException(
            "No active transformer found for topic" +
                    "${topicEntity.id.value}, ${topicEntity.name} to convert to schema ID: $toSchemaId"
        )
    }

    fun getTransformerEntityFromTopic(
        topicEntity: TopicEntity,
        toSchemaName: String
    ): TransformerEntity = transaction {
        require(topicEntity.defaultSchemaId != null) {
            "Topic ${topicEntity.id.value} does not have a default schema defined! Cannot find transformer."
        }
        val toSchemaId = Schemas
            .select { (Schemas.appId eq topicEntity.appId.value) and (Schemas.name eq toSchemaName) }
            .map { it[Schemas.id].value }
            .firstOrNull() ?: throw TransformerNotFoundException(
                "Schema not found for app ID: ${topicEntity.appId.value}, with name $toSchemaName, so no transformer can be found"
            )

        getTransformerEntityFromTopicEntity(topicEntity, toSchemaId) ?: throw TransformerNotFoundException(
            "No active transformer found for topic ${topicEntity.id.value},${topicEntity.name} to convert to schema $toSchemaName"
        )
    }

    private fun getTransformer(
        where: () -> org.jetbrains.exposed.sql.Op<Boolean>
    ): TransformerResponse = transaction {
        val toSchema = Schemas.alias("toSchema")
        val fromSchema = Schemas.alias("fromSchema")

        Transformers
            .join(Topics, JoinType.INNER, Transformers.topicId, Topics.id)
            .join(toSchema, JoinType.INNER, Transformers.toSchemaId, toSchema[Schemas.id])
            .join(fromSchema, JoinType.LEFT, Transformers.fromSchemaId, fromSchema[Schemas.id])
            .slice(
                Transformers.columns.toList() +
                        listOf(
                            Topics.id,
                            Topics.name,
                            toSchema[Schemas.id],
                            toSchema[Schemas.name],
                            fromSchema[Schemas.id],
                            fromSchema[Schemas.name]
                        )
            )
            .select { where() }
            .map { row ->
                val transformer = TransformerEntity.wrapRow(row)
                val fromSchemaName = row.getOrNull(fromSchema[Schemas.name])
                val toSchemaName = row[toSchema[Schemas.name]]
                val topicName = row[Topics.name]
                transformer.toResponse(fromSchemaName, toSchemaName, topicName)
            }
            .firstOrNull() ?: throw TransformerNotFoundException("Transfer not found for the provided app, topic, and transform IDs")
    }

    fun getTransformer(
        appId: Long,
        topicId: Long,
        transformerId: Long
    ): TransformerResponse = getTransformer { (Transformers.appId eq appId) and (Transformers.topicId eq topicId) and (Transformers.id eq transformerId) }


    fun getTransformer(
        appId: Long,
        topicName: String,
        transformerName: String
    ): TransformerResponse = getTransformer { (Transformers.appId eq appId) and (Transformers.name eq transformerName) and (Topics.name eq topicName) }


    fun updateTransformer(
        appId: Long,
        topicName: String,
        transformName: String,
        request: UpdateTransformerRequest
    ): TransformerResponse = transaction {
        // First get topicId and name (indexed lookup)
        val topicEntity = TopicEntity.find { (Topics.appId eq appId) and (Topics.name eq topicName) }
            .firstOrNull() ?: throw TransformerNotFoundException("Topic not found for appId=$appId and topicName=$topicName")

        // Then update with direct condition
        updateTransformer(
            topicEntity = topicEntity,
            where = { (Transformers.name eq transformName) and (Transformers.appId eq appId) },
            request = request) ?:
            throw TransformerNotFoundException("Transformer not found with name='$transformName' for topic '${topicEntity.name}'")
    }

    fun updateTransformer(
        appId: Long,
        topicId: Long,
        transformerId: Long,
        request: UpdateTransformerRequest
    ): TransformerResponse = transaction {
        val topicEntity = TopicEntity.find { (Topics.appId eq appId) and (Topics.id eq topicId) }
            .firstOrNull()
            ?: throw TransformerNotFoundException("Topic not found for appId=$appId and topicId=$topicId")

        updateTransformer(
            topicEntity = topicEntity,
            where = { (Transformers.id eq transformerId) and (Transformers.appId eq appId) },
            request = request) ?: throw TransformerNotFoundException("Transformer not found with id='$transformerId' for topic '${topicEntity.name}'"
        )
    }

    private fun updateTransformer(
        topicEntity: TopicEntity,
        where: () -> org.jetbrains.exposed.sql.Op<Boolean>,
        request: UpdateTransformerRequest
    ): TransformerResponse? {
        val transformer = TransformerEntity.find { where() }.firstOrNull() ?: return null
        // Make sure the transformer matches the entity
        require(transformer.topicId.value == topicEntity.id.value) {
            "Transformer ${transformer.id.value},${transformer.name} does not belong to topic ${topicEntity.id.value},${topicEntity.name}"
        }

        // If the name is changing, ensure it's available
        if (request.name != transformer.name) {
            if (TransformerEntity.find {
                (Transformers.appId eq topicEntity.appId) and (Transformers.name eq request.name)
            }.firstOrNull() != null) {
                throw DuplicateTransformerException("Transformer with name '${request.name}' already exists for app ${topicEntity.appId}")
            }
            transformer.name = request.name
        }

        // Convert and validate schema IDs from provided IDs or names
        val schemaInfo = schemaValidation.getTransformerRequestSchemaInfo(appId = topicEntity.appId.value, request)

        // If code provided, validate and update hash
        request.code?.let { newCode ->
            jsRuntime.validateTransformSyntax(newCode)
            transformer.code = newCode
            transformer.codeHash = generateCodeHash(newCode)
        }

        request.fromSchemaId?.let { transformer.fromSchemaId = EntityID(it, Schemas) }
        request.toSchemaId?.let { transformer.toSchemaId = EntityID(it, Schemas) }
        request.timeoutMs?.let { transformer.timeoutMs = it }
        request.enabled?.let { transformer.enabled = it }

        logger.info {
            "Updated transform: id=${transformer.name}, enabled=${transformer.enabled}"
        }

        return transformer.toResponse(fromSchemaName = schemaInfo.from?.name, toSchemaName = schemaInfo.to.name, topicName = topicEntity.name)
    }

    private fun deleteTransform(
        where: () -> Op<Boolean>
    ): Unit = transaction {
        val transformer = TransformerEntity.find { where() }.firstOrNull()
            ?: throw TransformerNotFoundException("Transform not found, check IDs provided")

        transformer.delete()

        logger.info { "Deleted transform: id=${transformer.id}, name=${transformer.name}" }
    }

    fun deleteTransform(
        appId: Long,
        topicId: Long,
        transformId: Long
    ): Unit = deleteTransform {
        (Transformers.id eq transformId) and
                (Transformers.appId eq appId) and
                (Transformers.topicId eq topicId)
    }

    fun deleteTransform(
        appId: Long,
        topicName: String,
        transformName: String
    ): Unit = deleteTransform {
        (Transformers.name eq transformName) and
                (Transformers.appId eq appId) and
                (Transformers.topicId inSubQuery Topics.slice(Topics.id).select {
                    (Topics.appId eq appId) and (Topics.name eq topicName)
                })
    }

    private fun generateCodeHash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val prefixedCode = "JS|$code"
        return digest.digest(prefixedCode.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun TransformerEntity.toResponse(fromSchemaName: String?, toSchemaName: String, topicName: String): TransformerResponse = TransformerResponse(
        id = this.id.value,
        appId = this.appId.value,
        topicId = this.topicId.value,
        topicName = topicName,
        name = this.name,
        fromSchemaId = this.fromSchemaId?.value,
        toSchemaId = this.toSchemaId.value,
        fromSchemaName = fromSchemaName,
        toSchemaName = toSchemaName,
        lang = this.lang.toString(),
        code = this.code,
        codeHash = this.codeHash,
        timeoutMs = this.timeoutMs,
        enabled = this.enabled,
        createdBy = this.createdBy,
        createdAt = this.createdAt.toString()
    )
}

