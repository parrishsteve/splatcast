package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.BatchPublishRequest
import co.vendistax.splatcast.models.PublishEventRequest
import co.vendistax.splatcast.services.PublishingService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.eventPublishingRoutes(
    eventPublishingService: PublishingService,
    logger: Logger = LoggerFactory.getLogger("eventPublishingRoutes"),
    ) {
    route("/apps/{appId}/topics/{topicId}") {

        get {
            val appId = call.parameters["appId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing topicId")
            //call.respond(HttpStatusCode.OK, mapOf("appId" to appId, "topicId" to topicId, "endpoints" to listOf("/publish", "/batch-publish")))
            call.respond(HttpStatusCode.OK)
        }

        // POST /apps/{appId}/topics/{topicId}/publish - Publish single event
        post("/publish") {
            val appId = call.parameters["appId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing topicId")

            val request = call.receive<PublishEventRequest>()

            eventPublishingService.publishEvent(appId, topicId, request)
                .onSuccess { response -> call.respond(HttpStatusCode.Created, response) }
                .onFailure { error ->
                    when {
                        error.message?.contains("not found") == true ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        error.message?.contains("Transform failed") == true ->
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message))
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                    }
                }
        }

        // POST /apps/{appId}/topics/{topicId}/batch-publish - Publish multiple events
        post("/batch-publish") {
            val appId = call.parameters["appId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing topicId")

            val request = call.receive<BatchPublishRequest>()

            if (request.events.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No events to publish"))
            }

            if (request.events.size > 100) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Batch size cannot exceed 100 events"))
            }

            eventPublishingService.batchPublish(appId, topicId, request)
                .onSuccess { response ->
                    val statusCode = if (response.failed.isEmpty()) HttpStatusCode.Created else HttpStatusCode.PartialContent
                    call.respond(statusCode, response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                }
        }
    }
}
