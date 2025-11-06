package co.vendistax.splatcast.database.tables

import co.vendistax.splatcast.database.PGEnum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import java.time.OffsetDateTime
import kotlin.toString

object Schemas : LongIdTable("schemas") {
    val appId = reference("app_id", Apps)
    val name = text("name")
    val jsonSchema = jsonb<JsonObject>(
        name = "json_schema",
        serialize = { it.toString() },
        deserialize = { Json.parseToJsonElement(it).jsonObject }
    )
    val status = customEnumeration("status", "schema_status",
        { value -> SchemaStatus.fromString(value as String) },
        { PGEnum("schema_status", it) }
    ).default(SchemaStatus.ACTIVE)
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { OffsetDateTime.now() }

    init {
        uniqueIndex(appId, name)
    }
}

enum class SchemaStatus {
    DRAFT, ACTIVE, DEPRECATED, UNKNOWN;
    override fun toString(): String = name.lowercase()
    companion object {
        fun fromString(value: String, default: SchemaStatus = ACTIVE): SchemaStatus =
            runCatching {
                SchemaStatus.valueOf(value.uppercase())
            }.getOrElse { default }

        fun getAllStatuses(): String =
            SchemaStatus.entries.filter { it != UNKNOWN }.joinToString(", ") { it.toString() }
    }
}