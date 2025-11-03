package co.vendistax.splatcast.services

import at.favre.lib.crypto.bcrypt.BCrypt
import co.vendistax.splatcast.database.tables.ApiKeyRole
import co.vendistax.splatcast.database.tables.ApiKeys
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.*
import com.github.f4b6a3.ulid.UlidCreator
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime

class ApiKeyNotFoundException(keyId: Long) :
    NoSuchElementException("API key not found: $keyId")

class InvalidApiKeyException(message: String = "Invalid API key") :
    IllegalArgumentException(message)

class ApiKeyService(
    private val logger: Logger = LoggerFactory.getLogger<ApiKeyService>(),
) {
    companion object {
        private const val MAX_LABEL_LENGTH = 100
    }

    fun create(appId: Long, request: CreateApiKeyRequest): ApiKeyCreatedResponse = transaction {
        validateApiKeyRequest(request)
        verifyAppExists(appId)

        val plainKey = "sk_${UlidCreator.getUlid()}"
        val keyHash = BCrypt.withDefaults().hashToString(12, plainKey.toCharArray())

        val keyId = ApiKeys.insertAndGetId {
            it[ApiKeys.appId] = appId
            it[ApiKeys.keyHash] = keyHash
            it[role] = request.role.toDbRole()
            it[label] = request.label
            it[createdAt] = OffsetDateTime.now()
        }

        logger.info { "Created API key: id=${keyId.value}, appId=$appId, label=${request.label}" }

        ApiKeyCreatedResponse(
            id = keyId.value,
            plainKey = plainKey,
            role = request.role,
            label = request.label,
            createdAt = Instant.now()
        )
    }

    fun findByAppId(appId: Long): List<ApiKeyResponse> = transaction {
        verifyAppExists(appId)

        ApiKeys.select { ApiKeys.appId eq appId }
            .map { toApiKeyResponse(it) }
    }

    fun findById(keyId: Long): ApiKeyResponse = transaction {
        ApiKeys.select { ApiKeys.id eq keyId }
            .singleOrNull()
            ?.let { toApiKeyResponse(it) }
            ?: throw ApiKeyNotFoundException(keyId)
    }

    fun delete(keyId: Long): Unit = transaction {
        val deleted = ApiKeys.deleteWhere { id eq keyId }

        if (deleted == 0) {
            throw ApiKeyNotFoundException(keyId)
        }

        logger.info { "Deleted API key: id=$keyId" }
    }

    fun verify(keyId: Long, plainKey: String): Boolean = transaction {
        require(plainKey.isNotBlank()) { "Plain key cannot be blank" }

        val row = ApiKeys.select { ApiKeys.id eq keyId }
            .singleOrNull()
            ?: throw ApiKeyNotFoundException(keyId)

        val keyHash = row[ApiKeys.keyHash]
        val isValid = BCrypt.verifyer().verify(plainKey.toCharArray(), keyHash).verified

        if (isValid) {
            ApiKeys.update({ ApiKeys.id eq keyId }) {
                it[lastUsedAt] = OffsetDateTime.now()
            }
            logger.debug { "API key verified: id=$keyId" }
        }

        isValid
    }

    fun verifyByPlainKey(plainKey: String): ApiKeyResponse = transaction {
        require(plainKey.isNotBlank()) { "Plain key cannot be blank" }

        ApiKeys.selectAll().forEach { row ->
            val keyHash = row[ApiKeys.keyHash]
            if (BCrypt.verifyer().verify(plainKey.toCharArray(), keyHash).verified) {
                val keyId = row[ApiKeys.id]
                ApiKeys.update({ ApiKeys.id eq keyId }) {
                    it[lastUsedAt] = OffsetDateTime.now()
                }

                logger.debug { "API key verified by plain key: id=${keyId.value}" }

                return@transaction toApiKeyResponse(row).copy(lastUsedAt = Instant.now())
            }
        }

        throw InvalidApiKeyException()
    }

    private fun validateApiKeyRequest(request: CreateApiKeyRequest) {
        require(request.label.isNotBlank()) { "API key label cannot be blank" }
        require(request.label.length <= MAX_LABEL_LENGTH) {
            "API key label exceeds maximum length of $MAX_LABEL_LENGTH"
        }
    }

    private fun verifyAppExists(appId: Long) {
        val appExists = transaction {
            co.vendistax.splatcast.database.tables.Apps
                .select { co.vendistax.splatcast.database.tables.Apps.id eq appId }
                .count() > 0
        }

        if (!appExists) {
            throw AppNotFoundException(appId = appId)
        }
    }

    private fun toApiKeyResponse(row: ResultRow): ApiKeyResponse = ApiKeyResponse(
        id = row[ApiKeys.id].value,
        appId = row[ApiKeys.appId].value,
        role = row[ApiKeys.role].toModelRole(),
        label = row[ApiKeys.label],
        createdAt = row[ApiKeys.createdAt].toInstant(),
        lastUsedAt = row[ApiKeys.lastUsedAt]?.toInstant()
    )
}

// Extension functions to convert between database and model roles
private fun co.vendistax.splatcast.models.ApiKeyRole.toDbRole(): co.vendistax.splatcast.database.tables.ApiKeyRole {
    return when (this) {
        co.vendistax.splatcast.models.ApiKeyRole.ADMIN -> co.vendistax.splatcast.database.tables.ApiKeyRole.ADMIN
        co.vendistax.splatcast.models.ApiKeyRole.SUBSCRIBER -> co.vendistax.splatcast.database.tables.ApiKeyRole.CONSUMER
        co.vendistax.splatcast.models.ApiKeyRole.PUBLISHER -> co.vendistax.splatcast.database.tables.ApiKeyRole.PRODUCER
        else -> {
            ApiKeyRole.UNKNOWN}
    }
}

private fun co.vendistax.splatcast.database.tables.ApiKeyRole.toModelRole(): co.vendistax.splatcast.models.ApiKeyRole {
    return when (this) {
        co.vendistax.splatcast.database.tables.ApiKeyRole.ADMIN -> co.vendistax.splatcast.models.ApiKeyRole.ADMIN
        co.vendistax.splatcast.database.tables.ApiKeyRole.CONSUMER -> co.vendistax.splatcast.models.ApiKeyRole.SUBSCRIBER
        co.vendistax.splatcast.database.tables.ApiKeyRole.PRODUCER -> co.vendistax.splatcast.models.ApiKeyRole.PUBLISHER
        else -> {
            co.vendistax.splatcast.models.ApiKeyRole.UNKNOWN}
    }
}
