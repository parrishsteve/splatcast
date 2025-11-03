package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.PublishEventRequest
import co.vendistax.splatcast.services.*
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.eventPublishingRoutes(
    appService: AppService,
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
    }
    route("/apps/by-name/{appName}/topics/{topicName}") {

        post("/publish") {
            val appName = call.parameters["appId"].validateRequired("appId")
            val topicName = call.parameters["topicId"].validateRequired("topicId")

            try {
                val request = call.receive<PublishEventRequest>()
                val idempotencyKey = call.request.headers["Idempotency-Key"]

                val app = appService.findByName(appName)
                val response = eventPublishingService.publishEvent(app.appId, topicName, request, idempotencyKey)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: SchemaVersionRequiredException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: SchemaMismatchException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: QueuePublishException) {
                logger.error(e, "Queue publish failed: app=$appName, topic=$topicName")
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Failed to publish to queue"))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to publish event: app=$appName, topic=$topicName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}
