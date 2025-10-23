package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.services.AuditService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.auditRoutes(auditService: AuditService) {
    route("/apps/{appId}/audit") {

        // GET /apps/{appId}/audit - Get all audit events for app
        get {
            val appId = call.parameters["appId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50

            if (pageSize > 100) {
                return@get call.respond(HttpStatusCode.BadRequest, "Page size cannot exceed 100")
            }

            val response = auditService.getEvents(appId, page, pageSize)
            call.respond(HttpStatusCode.OK, response)
        }

        // GET /apps/{appId}/audit?actor=xxx - Get events by actor
        get {
            val appId = call.parameters["appId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val actor = call.request.queryParameters["actor"]
            val action = call.request.queryParameters["action"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50

            val response = when {
                actor != null -> auditService.getEventsByActor(appId, actor, page, pageSize)
                action != null -> auditService.getEventsByAction(appId, action, page, pageSize)
                else -> auditService.getEvents(appId, page, pageSize)
            }

            call.respond(HttpStatusCode.OK, response)
        }
    }
}
