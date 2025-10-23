package co.vendistax.splatcast.services

import at.favre.lib.crypto.bcrypt.BCrypt
import co.vendistax.splatcast.database.tables.ApiKeys
import co.vendistax.splatcast.models.*
import com.github.f4b6a3.ulid.UlidCreator
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime

class ApiKeyService {

    fun create(appId: String, request: CreateApiKeyRequest): ApiKeyCreatedResponse = transaction {
        val keyId = "key_${UlidCreator.getUlid()}"
        val plainKey = "sk_${UlidCreator.getUlid()}"
        val keyHash = BCrypt.withDefaults().hashToString(12, plainKey.toCharArray())

        ApiKeys.insert {
            it[id] = keyId
            it[ApiKeys.appId] = appId
            it[ApiKeys.keyHash] = keyHash
            it[role] = request.role
            it[label] = request.label
            it[createdAt] = OffsetDateTime.now()
        }

        ApiKeyCreatedResponse(
            id = keyId,
            plainKey = plainKey,
            role = request.role,
            label = request.label,
            createdAt = Instant.now()
        )
    }

    fun findByAppId(appId: String): List<ApiKeyResponse> = transaction {
        ApiKeys.select { ApiKeys.appId eq appId }
            .map { row ->
                ApiKeyResponse(
                    id = row[ApiKeys.id].value,
                    appId = row[ApiKeys.appId],
                    role = row[ApiKeys.role],
                    label = row[ApiKeys.label],
                    createdAt = row[ApiKeys.createdAt].toInstant(),
                    lastUsedAt = row[ApiKeys.lastUsedAt]?.toInstant()
                )
            }
    }

    fun findById(keyId: String): ApiKeyResponse? = transaction {
        ApiKeys.select { ApiKeys.id eq keyId }
            .singleOrNull()
            ?.let { row ->
                ApiKeyResponse(
                    id = row[ApiKeys.id].value,
                    appId = row[ApiKeys.appId],
                    role = row[ApiKeys.role],
                    label = row[ApiKeys.label],
                    createdAt = row[ApiKeys.createdAt].toInstant(),
                    lastUsedAt = row[ApiKeys.lastUsedAt]?.toInstant()
                )
            }
    }

    fun delete(keyId: String) = transaction {
        ApiKeys.deleteWhere { id eq keyId }
    }

    fun verify(keyId: String, plainKey: String): Boolean = transaction {
        val keyHash = ApiKeys
            .select { ApiKeys.id eq keyId }
            .singleOrNull()
            ?.get(ApiKeys.keyHash)
            ?: return@transaction false

        // Update last used timestamp
        ApiKeys.update({ ApiKeys.id eq keyId }) {
            it[lastUsedAt] = OffsetDateTime.now()
        }

        BCrypt.verifyer().verify(plainKey.toCharArray(), keyHash).verified
    }

    fun verifyByPlainKey(plainKey: String): ApiKeyResponse? = transaction {
        ApiKeys.selectAll().forEach { row ->
            val keyHash = row[ApiKeys.keyHash]
            if (BCrypt.verifyer().verify(plainKey.toCharArray(), keyHash).verified) {
                // Update last used timestamp
                ApiKeys.update({ ApiKeys.id eq row[ApiKeys.id] }) {
                    it[lastUsedAt] = OffsetDateTime.now()
                }

                return@transaction ApiKeyResponse(
                    id = row[ApiKeys.id].value,
                    appId = row[ApiKeys.appId],
                    role = row[ApiKeys.role],
                    label = row[ApiKeys.label],
                    createdAt = row[ApiKeys.createdAt].toInstant(),
                    lastUsedAt = Instant.now()
                )
            }
        }
        null
    }
}
