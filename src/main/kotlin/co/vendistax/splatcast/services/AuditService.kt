package co.vendistax.splatcast.services

import co.vendistax.splatcast.models.AuditEventResponse
import co.vendistax.splatcast.models.AuditEventsListResponse
import co.vendistax.splatcast.models.CreateAuditEventRequest
import co.vendistax.splatcast.database.entities.AuditEventEntity
import co.vendistax.splatcast.database.tables.AuditEvents
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

interface AuditWriter {
    fun recordEvent(appId: String, request: CreateAuditEventRequest): String
    fun recordEvent(appId: String, actor: String, action: String, target: String, details: JsonObject? = null): String
}

interface AuditReader {
    fun getEvents(appId: String, page: Int = 1, pageSize: Int = 50): AuditEventsListResponse
    fun getEventsByActor(appId: String, actor: String, page: Int = 1, pageSize: Int = 50): AuditEventsListResponse
    fun getEventsByAction(appId: String, action: String, page: Int = 1, pageSize: Int = 50): AuditEventsListResponse
}

class AuditService : AuditWriter, AuditReader {

    override fun recordEvent(appId: String, request: CreateAuditEventRequest): String = transaction {
        val eventId = "audit_${System.currentTimeMillis()}"

        AuditEventEntity.new(eventId) {
            this.appId = appId
            this.actor = request.actor
            this.action = request.action
            this.target = request.target
            this.details = request.details ?: JsonObject(emptyMap())
        }

        eventId
    }

    override fun recordEvent(appId: String, actor: String, action: String, target: String, details: JsonObject?): String =
        recordEvent(appId, CreateAuditEventRequest(actor, action, target, details))

    override fun getEvents(appId: String, page: Int, pageSize: Int): AuditEventsListResponse = transaction {
        val offset = (page - 1) * pageSize

        val events = AuditEventEntity.find { AuditEvents.appId eq appId }
            .orderBy(AuditEvents.at to SortOrder.DESC)
            .limit(pageSize, offset.toLong())
            .map { it.toResponse() }

        val total = AuditEventEntity.find { AuditEvents.appId eq appId }.count().toInt()

        AuditEventsListResponse(
            events = events,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    override fun getEventsByActor(appId: String, actor: String, page: Int, pageSize: Int): AuditEventsListResponse = transaction {
        val offset = (page - 1) * pageSize

        val events = AuditEventEntity.find {
            AuditEvents.appId eq appId and (AuditEvents.actor eq actor)
        }
            .orderBy(AuditEvents.at to SortOrder.DESC)
            .limit(pageSize, offset.toLong())
            .map { it.toResponse() }

        val total = AuditEventEntity.find {
            AuditEvents.appId eq appId and (AuditEvents.actor eq actor)
        }.count().toInt()

        AuditEventsListResponse(
            events = events,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    override fun getEventsByAction(appId: String, action: String, page: Int, pageSize: Int): AuditEventsListResponse = transaction {
        val offset = (page - 1) * pageSize

        val events = AuditEventEntity.find {
            AuditEvents.appId eq appId and (AuditEvents.action eq action)
        }
            .orderBy(AuditEvents.at to SortOrder.DESC)
            .limit(pageSize, offset.toLong())
            .map { it.toResponse() }

        val total = AuditEventEntity.find {
            AuditEvents.appId eq appId and (AuditEvents.action eq action)
        }.count().toInt()

        AuditEventsListResponse(
            events = events,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    private fun AuditEventEntity.toResponse(): AuditEventResponse = AuditEventResponse(
        id = this.id.value,
        actor = this.actor,
        action = this.action,
        target = this.target,
        details = this.details,
        at = this.at.toString()
    )
}
