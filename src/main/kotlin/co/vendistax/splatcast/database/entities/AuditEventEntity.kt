package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.AuditEvents
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class AuditEventEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AuditEventEntity>(AuditEvents)

    var appId by AuditEvents.appId
    var actor by AuditEvents.actor
    var action by AuditEvents.action
    var target by AuditEvents.target
    var details by AuditEvents.details
    var at by AuditEvents.at
}
