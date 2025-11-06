package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import co.vendistax.splatcast.validation.*
import java.time.Instant

@Serializable
data class App(
    val appId: Long,
    val name: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
)

@Serializable
data class CreateAppRequest(val name: String) {
    fun validate(): CreateAppRequest {
        name.validateName("name")
        return this
    }
}

@Serializable
data class UpdateAppRequest(val name: String) {
    fun validate(): UpdateAppRequest {
        name.validateName("name")
        return this
    }
}
