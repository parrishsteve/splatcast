package co.vendistax.splatcast.database.tables

import co.vendistax.splatcast.database.PGEnum
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object ApiKeys : LongIdTable("api_keys") {
    val appId = reference("app_id", Apps)
    val role = customEnumeration("role", "api_key_role",
        { value -> ApiKeyRole.fromString(value as String) },
        { PGEnum("api_key_role", it) }
    )
    val label = text("label")
    val keyHash = text("key_hash").uniqueIndex()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()
}

enum class ApiKeyRole {
    PRODUCER, CONSUMER, ADMIN, UNKNOWN;
    override fun toString(): String = name.lowercase()
    companion object {
        fun fromString(value: String): ApiKeyRole =
            ApiKeyRole.valueOf(value.uppercase())
    }
}
