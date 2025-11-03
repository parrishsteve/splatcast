package co.vendistax.splatcast.services

import co.vendistax.splatcast.models.AuditEventResponse
import co.vendistax.splatcast.models.AuditEventsListResponse
import co.vendistax.splatcast.models.CreateAuditEventRequest
import co.vendistax.splatcast.database.entities.AuditEventEntity
import co.vendistax.splatcast.database.tables.Apps
import co.vendistax.splatcast.database.tables.AuditEvents
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.toString

class InvalidAuditEventException(message: String) : IllegalArgumentException(message)

interface AuditWriter {
    fun recordEvent(appId: Long, request: CreateAuditEventRequest): Long
    fun recordEvent(appId: Long, actor: String, action: String, target: String, details: JsonObject? = null): Long
}

interface AuditReader {
    fun getEvents(appId: Long, page: Int = 1, pageSize: Int = 50): AuditEventsListResponse
    fun getEventsByActor(appId: Long, actor: String, page: Int = 1, pageSize: Int = 50): AuditEventsListResponse
    fun getEventsByAction(appId: Long, action: String, page: Int = 1, pageSize: Int = 50): AuditEventsListResponse
}

class AuditService(
    private val logger: Logger = LoggerFactory.getLogger<AuditService>(),
) : AuditWriter, AuditReader {

    companion object {
        private const val MAX_PAGE_SIZE = 100
        private const val MAX_ACTOR_LENGTH = 255
        private const val MAX_ACTION_LENGTH = 100
        private const val MAX_TARGET_LENGTH = 255
    }

    override fun recordEvent(appId: Long, request: CreateAuditEventRequest): Long = transaction {
        validateAuditEventRequest(request)
        verifyAppExists(appId)

        val event = AuditEventEntity.new {
            this.appId = EntityID(appId, Apps)
            this.actor = request.actor
            this.action = request.action
            this.target = request.target
            this.details = request.details ?: JsonObject(emptyMap())
        }

        logger.info { "Recorded audit event: id=${event.id.value}, appId=$appId, actor=${request.actor}, action=${request.action}" }

        event.id.value
    }

    override fun recordEvent(appId: Long, actor: String, action: String, target: String, details: JsonObject?): Long =
        recordEvent(appId, CreateAuditEventRequest(actor, action, target, details))

    override fun getEvents(appId: Long, page: Int, pageSize: Int): AuditEventsListResponse = transaction {
        validatePagination(page, pageSize)
        verifyAppExists(appId)

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

    override fun getEventsByActor(appId: Long, actor: String, page: Int, pageSize: Int): AuditEventsListResponse = transaction {
        validatePagination(page, pageSize)
        verifyAppExists(appId)
        require(actor.isNotBlank()) { "Actor cannot be blank" }

        val offset = (page - 1) * pageSize

        val events = AuditEventEntity.find {
            (AuditEvents.appId eq appId) and (AuditEvents.actor eq actor)
        }
            .orderBy(AuditEvents.at to SortOrder.DESC)
            .limit(pageSize, offset.toLong())
            .map { it.toResponse() }

        val total = AuditEventEntity.find {
            (AuditEvents.appId eq appId) and (AuditEvents.actor eq actor)
        }.count().toInt()

        AuditEventsListResponse(
            events = events,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    override fun getEventsByAction(appId: Long, action: String, page: Int, pageSize: Int): AuditEventsListResponse = transaction {
        validatePagination(page, pageSize)
        verifyAppExists(appId)
        require(action.isNotBlank()) { "Action cannot be blank" }

        val offset = (page - 1) * pageSize

        val events = AuditEventEntity.find {
            (AuditEvents.appId eq appId) and (AuditEvents.action eq action)
        }
            .orderBy(AuditEvents.at to SortOrder.DESC)
            .limit(pageSize, offset.toLong())
            .map { it.toResponse() }

        val total = AuditEventEntity.find {
            (AuditEvents.appId eq appId) and (AuditEvents.action eq action)
        }.count().toInt()

        AuditEventsListResponse(
            events = events,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    private fun validateAuditEventRequest(request: CreateAuditEventRequest) {
        require(request.actor.isNotBlank()) { "Actor cannot be blank" }
        require(request.actor.length <= MAX_ACTOR_LENGTH) { "Actor exceeds maximum length of $MAX_ACTOR_LENGTH" }

        require(request.action.isNotBlank()) { "Action cannot be blank" }
        require(request.action.length <= MAX_ACTION_LENGTH) { "Action exceeds maximum length of $MAX_ACTION_LENGTH" }

        require(request.target.isNotBlank()) { "Target cannot be blank" }
        require(request.target.length <= MAX_TARGET_LENGTH) { "Target exceeds maximum length of $MAX_TARGET_LENGTH" }
    }

    private fun validatePagination(page: Int, pageSize: Int) {
        require(page > 0) { "Page must be greater than 0" }
        require(pageSize > 0) { "Page size must be greater than 0" }
        require(pageSize <= MAX_PAGE_SIZE) { "Page size cannot exceed $MAX_PAGE_SIZE" }
    }

    private fun verifyAppExists(appId: Long) {
        val appExists = transaction {
            Apps
                .select { Apps.id eq appId }
                .count() > 0
        }

        if (!appExists) {
            throw AppNotFoundException(appId = appId)
        }
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

