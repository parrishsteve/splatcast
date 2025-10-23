package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.AuditEvents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class AuditEventEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, AuditEventEntity>(AuditEvents)

    var appId by AuditEvents.appId
    var actor by AuditEvents.actor
    var action by AuditEvents.action
    var target by AuditEvents.target
    private var _rawDetails by AuditEvents.details
    var details: JsonObject
        get() = Json.parseToJsonElement(_rawDetails.orEmpty()).jsonObject
        set(value) { _rawDetails = value.toString() }
    var at by AuditEvents.at
}
