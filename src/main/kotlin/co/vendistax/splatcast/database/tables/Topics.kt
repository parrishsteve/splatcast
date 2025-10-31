package co.vendistax.splatcast.database.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object Topics : LongIdTable("topics") {
    val appId = reference("app_id", Apps)
    val name = text("name")
    val description = text("description").nullable()
    val retentionHours = integer("retention_hours").default(168)
    val defaultSchemaId = reference("default_schema_id", Schemas).nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { OffsetDateTime.now() }

    init {
        uniqueIndex(appId, name)
    }
}
