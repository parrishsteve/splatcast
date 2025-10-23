package co.vendistax.splatcast.database.tables

import co.vendistax.splatcast.database.PGEnum
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object Schemas : IdTable<String>("schemas") {
    override val id = text("id").entityId()
    val appId = text("app_id").references(Apps.id)
    val topicId = text("topic_id").references(Topics.id)
    val version = text("version")
    val jsonSchema = text("json_schema")
    val status = customEnumeration("status", "schema_status",
        { value -> SchemaStatus.fromString(value as String) },
        { PGEnum("schema_status", it) }
    ).default(SchemaStatus.ACTIVE)
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(appId, topicId, version)
    }
}

enum class SchemaStatus {
    DRAFT, ACTIVE, DEPRECATED;
    override fun toString(): String = name.lowercase()
    companion object {
        fun fromString(value: String): SchemaStatus =
            runCatching {
                SchemaStatus.valueOf(value.uppercase())
            }.getOrElse { ACTIVE }
    }
}
