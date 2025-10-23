package co.vendistax.splatcast.database.tables

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object AuditEvents : IdTable<String>("audit_events") {
    override val id = text("id").entityId()
    val appId = text("app_id").references(Apps.id)
    val actor = text("actor")
    val action = text("action")
    val target = text("target")
    val details = text("details").nullable()
    val at = timestampWithTimeZone("at").clientDefault { OffsetDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
