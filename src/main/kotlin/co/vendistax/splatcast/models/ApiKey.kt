package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
enum class ApiKeyRole {
    ADMIN, PUBLISHER, SUBSCRIBER, UNKNOWN;

    override fun toString(): String = name.lowercase()

    companion object {
        fun fromString(value: String): ApiKeyRole =
            runCatching {
                valueOf(value.uppercase())
            }.getOrElse { UNKNOWN }
    }
}

@Serializable
data class CreateApiKeyRequest(
    val role: ApiKeyRole,
    val label: String? = null
)

@Serializable
data class ApiKeyCreatedResponse(
    val id: String,
    val plainKey: String,  // Only returned on creation
    val role: ApiKeyRole,
    val label: String?,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant
)

@Serializable
data class ApiKeyResponse(
    val id: String,
    val appId: String,
    val role: ApiKeyRole,
    val label: String?,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val lastUsedAt: Instant?
)

@Serializable
data class VerifyApiKeyRequest(
    val plainKey: String
)

@Serializable
data class VerifyApiKeyResponse(
    val isValid: Boolean
)
