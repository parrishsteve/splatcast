package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.Config
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
import kotlin.text.get

private suspend fun handleException(
    e: Exception,
    logger: Logger,
    appIdentifier: String,
    topicIdentifier: String,
    call: io.ktor.server.application.ApplicationCall
) {
    when (e) {
        is TransformerNotFoundException -> {
            logger.error(e, "Transformer not found: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
        }
        is SchemaNotFoundException -> {
            logger.error(e, "Schema not found: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
        }
        is SchemaVersionRequiredException -> {
            logger.error(e, "Schema version required: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
        }
        is SchemaMismatchException -> {
            logger.error(e, "Schema mismatch: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.NotAcceptable, mapOf("error" to e.message))
        }
        is QueuePublishException -> {
            logger.error(e, "Queue publish failed: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to "Failed to publish to queue"))
        }
        is IllegalArgumentException -> {
            logger.error(e, "Invalid publish request argument: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
        }
        is QuotaExceededException -> {
            logger.error(e, "Quota exceeded: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to e.message))
        }
        else -> {
            logger.error(e, "Failed to publish event: app=$appIdentifier, topic=$topicIdentifier")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }
}


fun Route.eventPublishingRoutes(
    appService: AppService,
    eventPublishingService: PublishingService,
    logger: Logger = LoggerFactory.getLogger("eventPublishingRoutes"),
) {
    route("${Config.BASE_URL}/apps/{appId}/topics/{topicId}") {

        post("/publish") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()

            try {
                val request = call.receive<PublishEventRequest>()
                val idempotencyKey = call.request.headers["Idempotency-Key"]

                val response = eventPublishingService.publishEvent(appId, topicId, request, idempotencyKey)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                handleException(e, logger, appId.toString(), topicId.toString(), call)
            }
        }
        post("/publish/batch") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()

            try {
                val request = call.receive<BatchPublishRequest>()

                val response = eventPublishingService.batchPublishAsync(appId, topicId, request)
                call.respond(HttpStatusCode.MultiStatus, response)
            } catch (e: Exception) {
                handleException(e, logger, appId.toString(), topicId.toString(), call)
            }
        }
    }
    route("${Config.BASE_URL}/apps/${Config.NAME_URL_PREFACE}/{appName}/topics/{topicName}") {

        post("/publish") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val request = call.receive<PublishEventRequest>()

                val app = appService.findByName(appName)
                val response = eventPublishingService.publishEvent(app.appId, topicName, request)
                call.respond(HttpStatusCode.Created, response)
            }  catch (e: Exception) {
                handleException(e, logger, appName, topicName, call)
            }
        }
        post("/publish/batch") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val request = call.receive<BatchPublishRequest>()

                val app = appService.findByName(appName)
                val response = eventPublishingService.batchPublishAsync(app.appId, topicName, request)
                call.respond(HttpStatusCode.MultiStatus, response)
            } catch (e: Exception) {
                handleException(e, logger, appName, topicName, call)
            }
        }
    }
}
