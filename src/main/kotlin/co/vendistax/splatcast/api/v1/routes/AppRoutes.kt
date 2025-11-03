package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.CreateAppRequest
import co.vendistax.splatcast.models.UpdateAppRequest
import co.vendistax.splatcast.services.AppService
import co.vendistax.splatcast.services.AppNotFoundException
import co.vendistax.splatcast.services.AppNameAlreadyExistsException
import co.vendistax.splatcast.services.AppHasTopicsException
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.appRoutes(
    appService: AppService,
    logger: Logger = LoggerFactory.getLogger("appRoutes"),
) {
    route("/apps") {
        get {
            try {
                call.respond(appService.findAll())
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve apps")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        post {
            try {
                val request = call.receive<CreateAppRequest>()
                val app = appService.create(request)
                call.respond(HttpStatusCode.Created, app)
            } catch (e: AppNameAlreadyExistsException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create app")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // ID-based routes
        get("/{appId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@get
            }

            try {
                val app = appService.findById(appId)
                call.respond(app)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve app with id=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/{appId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@put
            }

            try {
                val request = call.receive<UpdateAppRequest>()
                val app = appService.update(appId, request)
                call.respond(app)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: AppNameAlreadyExistsException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update app with id=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{appId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@delete
            }

            try {
                appService.delete(appId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: AppHasTopicsException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete app with id=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // Name-based routes
        get("/by-name/{appName}") {
            val appName = call.parameters["appName"].validateRequired("appName")

            try {
                val app = appService.findByName(appName)
                call.respond(app)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve app with name=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/by-name/{appName}") {
            val appName = call.parameters["appName"].validateRequired("appName")

            try {
                val existingApp = appService.findByName(appName)
                val request = call.receive<UpdateAppRequest>()
                val app = appService.update(existingApp.appId, request)
                call.respond(app)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: AppNameAlreadyExistsException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update app with name=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/by-name/{appName}") {
            val appName = call.parameters["appName"].validateRequired("appName")

            try {
                val existingApp = appService.findByName(appName)
                appService.delete(existingApp.appId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: AppHasTopicsException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete app with name=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}

