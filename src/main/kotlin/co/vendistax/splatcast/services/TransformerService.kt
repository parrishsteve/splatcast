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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest

class InvalidTransformCodeException(message: String) : IllegalArgumentException(message)

class DuplicateTransformerException(message: String) : IllegalStateException(message)

class TransformerExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class TransformerNotFoundException(message: String) : NoSuchElementException(message)

class TransformerService(
    private val jsRuntime: JavaScriptRuntimeService,
    private val logger: Logger = LoggerFactory.getLogger<TransformerService>(),
) {

    fun createTransform(
        appId: Long,
        topicId: Long,
        request: CreateTransformerRequest
    ): TransformerResponse = transaction {
        // Verify topic exists and belongs to app
        TopicEntity.find {
            (Topics.id eq topicId) and (Topics.appId eq appId)
        }.firstOrNull()
            ?: throw TransformerNotFoundException("Topic not found: appId=$appId, topicId=$topicId")

        // Validate JavaScript syntax before saving
        jsRuntime.validateTransformSyntax(request.code)

        val codeHash = generateCodeHash(request.code)

        // Check for duplicate active transform with same hash
        val existingTransform = TransformerEntity.find {
            (Transformers.appId eq appId) and
                    (Transformers.name eq request.name) and
                    (Transformers.topicId eq topicId) and
                    (Transformers.fromSchemaId eq request.fromSchemaId) and
                    (Transformers.toSchemaId eq request.toSchemaId) and
                    (Transformers.codeHash eq codeHash) and
                    (Transformers.enabled eq true)
        }.firstOrNull()

        if (existingTransform != null) {
            throw DuplicateTransformerException(
                "Active transform already exists: ${request.fromSchemaId} -> ${request.toSchemaId}"
            )
        }

        val transform = TransformerEntity.new {
            this.appId = EntityID(appId, Apps)
            this.topicId = EntityID(topicId, Topics)
            this.name = request.name
            this.fromSchemaId = request.fromSchemaId?.let { EntityID(it, Schemas) }
            this.toSchemaId = EntityID(request.toSchemaId, Schemas)
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

        transform.toResponse()
    }

    fun executeTransform(
        transformerId: Long,
        inputData: JsonObject
    ): TransformerExecutionResult = transaction {
        val transformer = TransformerEntity.findById(transformerId)
            ?: throw TransformerNotFoundException("Transform not found: id=$transformerId")

        executeTransform(transformer, inputData)
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

    fun getTransformers(
        appId: Long,
        topicId: Long
    ): List<TransformerResponse> = transaction {
        TransformerEntity.find {
            (Transformers.appId eq appId) and (Transformers.topicId eq topicId)
        }.map { it.toResponse() }
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

    fun getTransformer(
        appId: Long,
        topicId: Long,
        transformerId: Long
    ): TransformerResponse = transaction {
        val transformer = TransformerEntity.findById(transformerId)
            ?: throw TransformerNotFoundException("Transform not found: id=$transformerId")

        if (transformer.appId.value != appId || transformer.topicId.value != topicId) {
            throw TransformerNotFoundException(
                "Transform not found: id=$transformerId in app=$appId, topic=$topicId"
            )
        }

        transformer.toResponse()
    }

    fun updateTransform(
        appId: Long,
        topicId: Long,
        transformId: Long,
        request: UpdateTransformerRequest
    ): TransformerResponse = transaction {
        val transformer = TransformerEntity.findById(transformId)
            ?: throw TransformerNotFoundException("Transform not found: id=$transformId")

        if (transformer.appId.value != appId || transformer.topicId.value != topicId) {
            throw TransformerNotFoundException(
                "Transform not found: id=$transformId in app=$appId, topic=$topicId"
            )
        }

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
            "Updated transform: id=$transformId, enabled=${transformer.enabled}"
        }

        transformer.toResponse()
    }

    fun deleteTransform(
        appId: Long,
        topicId: Long,
        transformId: Long
    ): Unit = transaction {
        val transformer = TransformerEntity.findById(transformId)
            ?: throw TransformerNotFoundException("Transform not found: id=$transformId")

        if (transformer.appId.value != appId || transformer.topicId.value != topicId) {
            throw TransformerNotFoundException(
                "Transform not found: id=$transformId in app=$appId, topic=$topicId"
            )
        }

        transformer.delete()

        logger.info { "Deleted transform: id=$transformId" }
    }

    private fun generateCodeHash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val prefixedCode = "JS|$code"
        return digest.digest(prefixedCode.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun TransformerEntity.toResponse(): TransformerResponse = TransformerResponse(
        id = this.id.value,
        appId = this.appId.value,
        topicId = this.topicId.value,
        name = this.name,
        fromSchemaId = this.fromSchemaId?.value,
        toSchemaId = this.toSchemaId.value,
        lang = this.lang.toString(),
        code = this.code,
        codeHash = this.codeHash,
        timeoutMs = this.timeoutMs,
        enabled = this.enabled,
        createdBy = this.createdBy,
        createdAt = this.createdAt.toString()
    )
}

