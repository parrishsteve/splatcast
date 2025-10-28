package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.CreateAppRequest
import co.vendistax.splatcast.models.UpdateAppRequest
import co.vendistax.splatcast.services.ApiKeyService
import co.vendistax.splatcast.services.AppService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.appRoutes(
    appService: AppService,
    logger: Logger = LoggerFactory.getLogger("appRoutes"),
    ) {
    route("/api/apps") {
        get {
            call.respond(appService.findAll())
        }

        get("/{appId}") {
            val appId = call.parameters["appId"] ?: throw IllegalArgumentException("Missing appId")
            val app = appService.findById(appId)
            if (app != null) {
                call.respond(app)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post {
            val request = call.receive<CreateAppRequest>()
            call.respond(HttpStatusCode.Created, appService.create(request))
        }

        put("/{appId}") {
            val appId = call.parameters["appId"] ?: throw IllegalArgumentException("Missing appId")
            val request = call.receive<UpdateAppRequest>()
            call.respond(appService.update(appId, request))
        }

        delete("/{appId}") {
            val appId = call.parameters["appId"] ?: throw IllegalArgumentException("Missing appId")
            appService.delete(appId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
