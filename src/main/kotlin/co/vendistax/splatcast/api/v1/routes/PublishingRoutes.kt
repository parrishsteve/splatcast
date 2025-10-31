package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.BatchPublishRequest
import co.vendistax.splatcast.models.PublishEventRequest
import co.vendistax.splatcast.services.*
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.eventPublishingRoutes(
    eventPublishingService: PublishingService,
    logger: Logger = LoggerFactory.getLogger("eventPublishingRoutes"),
) {
    route("/apps/{appId}/topics/{topicId}") {

        post("/publish") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()

            try {
                val request = call.receive<PublishEventRequest>()
                val idempotencyKey = call.request.headers["Idempotency-Key"]

                val response = eventPublishingService.publishEvent(appId, topicId, request, idempotencyKey)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: SchemaVersionRequiredException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: SchemaMismatchException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: QueuePublishException) {
                logger.error(e, "Queue publish failed: app=$appId, topic=$topicId")
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Failed to publish to queue"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to publish event: app=$appId, topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        post("/batch-publish") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()

            try {
                val request = call.receive<BatchPublishRequest>()

                if (request.events.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No events to publish"))
                }

                if (request.events.size > 100) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Batch size cannot exceed 100 events"))
                }

                val response = eventPublishingService.batchPublish(appId, topicId, request)

                val statusCode = if (response.failed.isEmpty()) {
                    HttpStatusCode.Created
                } else {
                    HttpStatusCode.PartialContent
                }

                call.respond(statusCode, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to batch publish: app=$appId, topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}
