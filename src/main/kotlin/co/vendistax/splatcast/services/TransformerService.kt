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
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import kotlin.Boolean
import kotlin.and
import kotlin.collections.get
import kotlin.plus
import kotlin.text.get

class InvalidTransformCodeException(message: String) : IllegalArgumentException(message)

class DuplicateTransformerException(message: String) : IllegalStateException(message)

class TransformerExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class TransformerNotFoundException(message: String) : NoSuchElementException(message)

class TransformerService(
    private val jsRuntime: JavaScriptRuntimeService,
    private val schemaValidationService: SchemaValidationService,
    private val logger: Logger = LoggerFactory.getLogger<TransformerService>(),
) {


    fun createTransform(
        appId: Long,
        topicName: String,
        request: CreateTransformerRequest
    ): TransformerResponse =
        createTransform(appId, where = {
            (Topics.name eq topicName) and (Topics.appId eq appId)
        }, request)

    fun createTransform(
        appId: Long,
        topicId: Long,
        request: CreateTransformerRequest
    ): TransformerResponse =
        createTransform(appId, where = {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }, request)

    private fun createTransform(
        appId: Long,
        where: () -> org.jetbrains.exposed.sql.Op<Boolean>,
        request: CreateTransformerRequest
    ): TransformerResponse = transaction {
        // Verify topic exists and belongs to app
        val topic = TopicEntity.find { where() }.firstOrNull()
            ?: throw TransformerNotFoundException("Topic not found, check IDs provided")

        // Convert and validate schema IDs from provided IDs or names
        val schemaInfo = schemaValidationService.getTransformerRequestSchemaInfo(appId, request)

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
                "Active transform already exists: ${schemaInfo.from} -> ${schemaInfo.to}"
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

        transform.toResponse(schemaInfo.from?.toString(), schemaInfo.to.name)
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

    fun testTransform(
        transformId: Long,
        request: TransformerTestRequest
    ): TransformerTestResult = transaction {
        val transform = TransformerEntity.findById(transformId)
            ?: throw TransformerNotFoundException("Transform not found: id=$transformId")

        // Execute transform on test input - catch execution errors
        val actualOutput: JsonObject?
        var error: String? = null
        val executionTimeMs: Long

        val startTime = System.currentTimeMillis()
        actualOutput = jsRuntime.executeTransform(
            transform.code,
            request.inputJson,
            transform.timeoutMs
        ).getOrElse {
            error = it.message
            null
        }
        executionTimeMs = System.currentTimeMillis() - startTime

        val matches = actualOutput != null && actualOutput == request.expectJson

        TransformerTestResult(
            transformerId = transformId,
            inputJson = request.inputJson,
            expectedJson = request.expectJson,
            actualJson = actualOutput,
            matches = matches,
            executionTimeMs = executionTimeMs,
            error = error
        )
    }

    /*fun getTransformersOrg(
        appId: Long,
        topicId: Long
    ): List<TransformerResponse> = transaction {
        TransformerEntity.find {
            (Transformers.appId eq appId) and (Transformers.topicId eq topicId)
        }.map {
            val schemaInfo = schemaValidationService.getTransformerSchemaInfo(it)
            it.toResponse(schemaInfo.from?.name, schemaInfo.to.name)
        }
    }*/

    private fun getTransformers(
        where: () -> org.jetbrains.exposed.sql.Op<Boolean>
    ): List<TransformerResponse> = transaction {
        val toSchema = Schemas.alias("toSchema")
        val fromSchema = Schemas.alias("fromSchema")

        Transformers
            .join(Topics, JoinType.INNER, Transformers.topicId, Topics.id)
            .join(toSchema, JoinType.INNER, Transformers.toSchemaId, toSchema[Schemas.id])
            .join(fromSchema, JoinType.LEFT, Transformers.fromSchemaId, fromSchema[Schemas.id])
            .slice(
                Transformers.columns.toList() +
                        Topics.columns.toList() +
                        listOf(
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
                transformer.toResponse(fromSchemaName, toSchemaName)
            }
    }

    fun getTransformers(
        appId: Long,
        transformerName: String
    ): List<TransformerResponse> = transaction {
        getTransformers { (Transformers.appId eq appId) and (Transformers.name eq transformerName) }
    }

    fun getTransformers(
        appId: Long,
        transformerId: Long
    ): List<TransformerResponse> = transaction {
        getTransformers { (Transformers.appId eq appId) and (Transformers.id eq transformerId) }
    }

    fun getTransformerEntity(
        appId: Long,
        topicId: Long,
        transformerId: Long
    ): TransformerEntity? = transaction {
        val entity = TransformerEntity.findById(transformerId)
            ?: throw TransformerNotFoundException("Transform not found: id=$transformerId")
        if (entity.appId.value != appId || entity.topicId.value != topicId) return@transaction null
        entity
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
                        Topics.columns.toList() +
                        listOf(
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
                transformer.toResponse(fromSchemaName, toSchemaName)
            }
            .firstOrNull() ?: throw TransformerNotFoundException("Transfer not found for th provided app, topic, and transform IDs")
    }

    fun getTransformer(
        appId: Long,
        topicId: Long,
        transformerId: Long
    ): TransformerResponse =
        getTransformer { (Transformers.appId eq appId) and (Transformers.topicId eq topicId) and (Transformers.id eq transformerId) }

    fun getTransformer(
        appId: Long,
        topicName: String,
        transformerName: String
    ): TransformerResponse =
        getTransformer { (Transformers.appId eq appId) and (Transformers.name eq topicName) and (Transformers.name eq transformerName) }


    fun updateTransform(
        appId: Long,
        topicName: String,
        transformName: String,
        request: UpdateTransformerRequest
    ): TransformerResponse =
        updateTransform(appId, where = {
            (Transformers.name eq transformName) and
                    (Transformers.appId eq appId) and
                    (Transformers.topicId inSubQuery Topics.slice(Topics.id).select {
                        (Topics.appId eq appId) and (Topics.name eq topicName)
                    })
        }, request)


    fun updateTransform(
        appId: Long,
        topicId: Long,
        transformerId: Long,
        request: UpdateTransformerRequest
    ): TransformerResponse =
        updateTransform(appId, where = {
            (Transformers.id eq transformerId) and
                    (Transformers.appId eq appId) and
                    (Transformers.topicId eq topicId)
        }, request)


    //(Transformers.id eq transformId) and (Transformers.appId eq appId) and (Transformers.topicId eq topicId)
    private fun updateTransform(
        appId: Long,
        where: () -> org.jetbrains.exposed.sql.Op<Boolean>,
        request: UpdateTransformerRequest
    ): TransformerResponse = transaction {
        val transformer = TransformerEntity.find { where() }.firstOrNull() ?: throw TransformerNotFoundException("Transform not found, check provided IDs")

        // Convert and validate schema IDs from provided IDs or names
        val schemaInfo = schemaValidationService.getTransformerRequestSchemaInfo(appId, request)

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

        transformer.toResponse(schemaInfo.from?.name, schemaInfo.to.name)
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

    private fun TransformerEntity.toResponse(fromSchemaName: String?, toSchemaName: String): TransformerResponse = TransformerResponse(
        id = this.id.value,
        appId = this.appId.value,
        topicId = this.topicId.value,
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

