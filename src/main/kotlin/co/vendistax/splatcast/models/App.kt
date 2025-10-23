package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class App(
    val appId: String,
    val name: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
)

@Serializable
data class CreateAppRequest(val name: String)

@Serializable
data class UpdateAppRequest(val name: String)
