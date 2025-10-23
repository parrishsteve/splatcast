package co.vendistax.splatcast.database.tables

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object Apps : IdTable<String>("apps") {
    override val id = text("app_id").entityId()
    val name = text("name")
    val createdAt = timestampWithTimeZone("created_at").clientDefault {  OffsetDateTime.now() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault {  OffsetDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
