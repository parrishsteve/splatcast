package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import co.vendistax.splatcast.validation.*

@Serializable
data class CreateTopicRequest(
    val name: String,
    val description: String? = null,
    val retentionHours: Int = 168,
    val defaultSchemaId: Long? = null,
    val defaultSchemaName: String? = null,
    val quotas: QuotaSettings = QuotaSettings()
) {
    fun validate(): CreateTopicRequest {
        name.validateName("name")
        description?.validateLength("description", max = 500)

        if (retentionHours <= 0) {
            throw ValidationException("retentionHours must be greater than 0")
        }
        quotas.validate()
        return this
    }
}

@Serializable
data class QuotaSettings(
    val perMinute: Int = 6000,
    val perDay: Int = 1000000
) {
    fun validate(): QuotaSettings {
        if (perMinute <= 0) {
            throw ValidationException("perMinute must be greater than 0")
        }
        if (perDay <= 0) {
            throw ValidationException("perDay must be greater than 0")
        }
        return this
    }
}

@Serializable
data class PatchTopicRequest(
    val defaultSchemaId: Long? = null,
    val defaultSchemaName: String? = null
)

@Serializable
data class TopicResponse(
    val id: Long,
    val appId: Long,
    val name: String,
    val description: String?,
    val retentionHours: Int = 168,
    val defaultSchemaId: Long?,
    val defaultSchemaName: String?,
    val quotas: QuotaSettings,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class UpdateTopicRequest(
    val name: String? = null,
    val description: String? = null,
    val retentionHours: Int? = null,
    val defaultSchemaId: Long? = null,
    val defaultSchemaName: String?,
    val quotas: QuotaSettings? = null
) {
    fun validate(): UpdateTopicRequest {
        name?.validateName("name")
        description?.validateLength("description", max = 500)

        retentionHours?.let {
            if (it <= 0) {
                throw ValidationException("retentionHours must be greater than 0")
            }
        }
        quotas?.validate()
        return this
    }
}
