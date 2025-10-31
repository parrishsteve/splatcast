package co.vendistax.splatcast.database.tables

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import java.time.OffsetDateTime

object AuditEvents : LongIdTable("audit_events") {
    val appId = reference("app_id", Apps)
    val actor = text("actor")
    val action = text("action")
    val target = text("target")
    val details = jsonb<JsonObject>(
        name = "details",
        serialize = { it.toString() },
        deserialize = { Json.parseToJsonElement(it).jsonObject }
    )
    val at = timestampWithTimeZone("at").clientDefault { OffsetDateTime.now() }
}
