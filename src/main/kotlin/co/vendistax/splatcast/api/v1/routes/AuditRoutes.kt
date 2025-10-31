package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.services.AppNotFoundException
import co.vendistax.splatcast.services.AuditService
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.auditRoutes(
    auditService: AuditService,
    logger: Logger = LoggerFactory.getLogger("auditRoutes"),
) {
    route("/apps/{appId}/audit") {
        get {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()

            try {
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
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve audit events for app with id=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}
