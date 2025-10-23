package co.vendistax.splatcast.services

import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.database.entities.TransformerEntity
import co.vendistax.splatcast.database.entities.TopicEntity
import co.vendistax.splatcast.database.tables.Transformers
import co.vendistax.splatcast.database.tables.Topics
import co.vendistax.splatcast.database.tables.TransformLang
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest

class TransformerService {

    fun createTransform(appId: String, topicId: String, request: CreateTransformerRequest): Result<TransformerResponse> = transaction {
        try {
            // Verify topic exists and belongs to app
            val topic = TopicEntity.find {
                Topics.id eq topicId and (Topics.appId eq appId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Topic not found"))

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

    fun getTransforms(appId: String, topicId: String): Result<TransformerListResponse> = transaction {
        try {
            val transforms = TransformerEntity.find {
                Transformers.appId eq appId and (Transformers.topicId eq topicId)
            }.map { it.toResponse() }

            Result.success(TransformerListResponse(transforms, transforms.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTransform(appId: String, topicId: String, transformId: String): Result<TransformerResponse> = transaction {
        try {
            val transform = TransformerEntity.find {
                Transformers.id eq transformId and
                        (Transformers.appId eq appId) and
                        (Transformers.topicId eq topicId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Transform not found"))

            Result.success(transform.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateTransform(appId: String, topicId: String, transformId: String, request: UpdateTransformerRequest): Result<TransformerResponse> = transaction {
        try {
            val transform = TransformerEntity.find {
                Transformers.id eq transformId and
                        (Transformers.appId eq appId) and
                        (Transformers.topicId eq topicId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Transform not found"))

            request.code?.let {
                transform.code = it
                transform.codeHash = generateCodeHash(it)
            }
            request.timeoutMs?.let { transform.timeoutMs = it }
            request.enabled?.let { transform.enabled = it }

            Result.success(transform.toResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteTransform(appId: String, topicId: String, transformId: String): Result<Unit> = transaction {
        try {
            val transform = TransformerEntity.find {
                Transformers.id eq transformId and
                        (Transformers.appId eq appId) and
                        (Transformers.topicId eq topicId)
            }.firstOrNull()
                ?: return@transaction Result.failure(Exception("Transform not found"))

            transform.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateCodeHash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val prefixedCode = "JS|$code"
        return digest.digest(prefixedCode.toByteArray()).joinToString("") { "%02x".format(it) }
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
