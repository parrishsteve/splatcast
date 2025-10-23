package co.vendistax.splatcast.database.tables

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Topics : IdTable<String>("topics") {
    override val id = text("id").entityId()
    val appId = text("app_id").references(Apps.id)
    val name = text("name")
    val description = text("description").nullable()
    val retentionHours = integer("retention_hours").default(72)
    val defaultSchemaId = text("default_schema_id").references(Schemas.id).nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { java.time.OffsetDateTime.now() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { java.time.OffsetDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}