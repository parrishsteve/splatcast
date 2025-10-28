package co.vendistax.splatcast.services

import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.entities.TransformerEntity
import co.vendistax.splatcast.database.tables.Topics
import co.vendistax.splatcast.database.tables.TransformLang
import co.vendistax.splatcast.database.tables.Transformers
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest

class TransformerService(
    private val jsRuntime: JavaScriptRuntimeService,
    private val logger: Logger = LoggerFactory.getLogger<TransformerService>(),
) {

    fun createTransform(appId: String, topicId: String, request: CreateTransformerRequest): Result<TransformerResponse> = transaction {
        try {
            // Verify topic exists and belongs to app
            val topic = TopicEntity.find {
                Topics.id eq topicId and (Topics.appId eq appId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Topic not found"))

            // Validate JavaScript syntax before saving
            jsRuntime.validateTransformSyntax(request.code).onFailure { error ->
                return@transaction Result.failure(error)
            }

            val transformId = "trf_${System.currentTimeMillis()}"
            val codeHash = generateCodeHash(request.code)

            // Check for duplicate active transform with same hash
            val existingTransform = TransformerEntity.find {
                Transformers.appId eq appId and
                        (Transformers.topicId eq topicId) and
                        (Transformers.fromSchema eq request.fromSchema) and
                        (Transformers.toSchema eq request.toSchema) and
                        (Transformers.codeHash eq codeHash) and
                        (Transformers.enabled eq true)
            }.firstOrNull()

            if (existingTransform != null) {
                return@transaction Result.failure(Exception("Active transform with same code already exists"))
            }

            val transform = TransformerEntity.new(transformId) {
                this.appId = appId
                this.topicId = topicId
                this.fromSchema = request.fromSchema
                this.toSchema = request.toSchema
                this.lang = TransformLang.JS
                this.code = request.code
                this.codeHash = codeHash
                this.timeoutMs = request.timeoutMs
                this.enabled = request.enabled
                this.createdBy = request.createdBy
            }

            Result.success(transform.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun executeTransform(
        transformerId: String,
        inputData: JsonObject
    ): Result<TransformerExecutionResult> = transaction {
        logger.info { "Executing transform with ID: $transformerId" }
        val transformer: TransformerEntity
        try {
            transformer = TransformerEntity.findById(transformerId)
                ?: return@transaction Result.failure(Exception("Transform not found"))
        } catch (e: Exception) {
            return@transaction Result.failure(e)
        }
        executeTransform(transformer, inputData)
    }

    fun executeTransform(
        transformer: TransformerEntity,
        inputData: JsonObject
    ): Result<TransformerExecutionResult> {
        return try {
            jsRuntime.executeTransform(transformer.code, inputData, transformer.timeoutMs)
                .map { transformedData ->
                    TransformerExecutionResult(
                        transformId = transformer.id.value,
                        fromSchema = transformer.fromSchema,
                        toSchema = transformer.toSchema,
                        originalData = inputData,
                        transformedData = transformedData
                    )
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun testTransform(transformId: String, request: TransformerTestRequest): Result<TransformerTestResult> = transaction {
        try {
            val transform = TransformerEntity.findById(transformId)
                ?: return@transaction Result.failure(Exception("Transform not found"))

            // Execute transform on test input
            val executionResult = jsRuntime.executeTransform(
                transform.code,
                request.inputJson,
                transform.timeoutMs
            )

            // Fold the JsonObject Result into a Result<TransformerTestResult> (always returning Result.success with details)
            return@transaction executionResult.fold(
                onSuccess = { actualOutput ->
                    val matches = actualOutput == request.expectJson
                    Result.success(TransformerTestResult(
                        transformId = transformId,
                        inputJson = request.inputJson,
                        expectedJson = request.expectJson,
                        actualJson = actualOutput,
                        matches = matches,
                        executionTimeMs = 0
                    ))
                },
                onFailure = { error ->
                    Result.success(TransformerTestResult(
                        transformId = transformId,
                        inputJson = request.inputJson,
                        expectedJson = request.expectJson,
                        actualJson = null,
                        matches = false,
                        executionTimeMs = 0,
                        error = error.message
                    ))
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ... existing methods (getTransforms, getTransform, updateTransform, deleteTransform)

    private fun generateCodeHash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val prefixedCode = "JS|$code"
        return digest.digest(prefixedCode.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun getTransforms(appId: String, topicId: String): Result<List<TransformerResponse>> = transaction {
        try {
            val list = TransformerEntity.find {
                Transformers.appId eq appId and (Transformers.topicId eq topicId)
            }.map { it.toResponse() }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTransform(appId: String, topicId: String, transformId: String): Result<TransformerResponse> = transaction {
        try {
            val t = TransformerEntity.findById(transformId)
                ?: return@transaction Result.failure(Exception("Transform not found"))
            if (t.appId != appId || t.topicId != topicId) {
                return@transaction Result.failure(Exception("Transform does not belong to app/topic"))
            }
            Result.success(t.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateTransform(
        appId: String,
        topicId: String,
        transformId: String,
        request: UpdateTransformerRequest
    ): Result<TransformerResponse> = transaction {
        try {
            val t = TransformerEntity.findById(transformId)
                ?: return@transaction Result.failure(Exception("Transform not found"))
            if (t.appId != appId || t.topicId != topicId) {
                return@transaction Result.failure(Exception("Transform does not belong to app/topic"))
            }

            // If code provided, validate and update hash
            request.code?.let { newCode ->
                jsRuntime.validateTransformSyntax(newCode).onFailure { err -> return@transaction Result.failure(err) }
                t.code = newCode
                t.codeHash = generateCodeHash(newCode)
            }

            request.fromSchema?.let { t.fromSchema = it }
            request.toSchema?.let { t.toSchema = it }
            request.timeoutMs?.let { t.timeoutMs = it }
            request.enabled?.let { t.enabled = it }

            Result.success(t.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteTransform(appId: String, topicId: String, transformId: String): Result<Unit> = transaction {
        try {
            val t = TransformerEntity.findById(transformId)
                ?: return@transaction Result.failure(Exception("Transform not found"))
            if (t.appId != appId || t.topicId != topicId) {
                return@transaction Result.failure(Exception("Transform does not belong to app/topic"))
            }
            // remove or disable depending on your model; here we delete the entity
            t.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun TransformerEntity.toResponse(): TransformerResponse = TransformerResponse(
        id = this.id.value,
        appId = this.appId,
        topicId = this.topicId,
        fromSchema = this.fromSchema,
        toSchema = this.toSchema,
        lang = this.lang.toString(),
        code = this.code,
        codeHash = this.codeHash,
        timeoutMs = this.timeoutMs,
        enabled = this.enabled,
        createdBy = this.createdBy,
        createdAt = this.createdAt.toString()
    )
}
