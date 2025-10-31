package co.vendistax.splatcast.database.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object Apps : LongIdTable("apps") {
    val name = text("name").uniqueIndex()
    val createdAt = timestampWithTimeZone("created_at").clientDefault {  OffsetDateTime.now() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault {  OffsetDateTime.now() }
}
