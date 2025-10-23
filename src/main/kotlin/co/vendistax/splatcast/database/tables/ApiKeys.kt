package co.vendistax.splatcast.database.tables

import co.vendistax.splatcast.database.PGEnum
import co.vendistax.splatcast.models.ApiKeyRole
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.Instant
import java.time.OffsetDateTime

object ApiKeys : IdTable<String>("api_keys") {
    override val id = text("id").entityId()
    val appId = text("app_id").references(Apps.id)
    val keyHash = text("key_hash")
    val role = customEnumeration(
        "role",
        "api_key_role",
        { value -> ApiKeyRole.fromString(value as String) },
        { PGEnum("api_key_role", it) }
    )
    val label = text("label").nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()

    override val primaryKey = PrimaryKey(id)
}