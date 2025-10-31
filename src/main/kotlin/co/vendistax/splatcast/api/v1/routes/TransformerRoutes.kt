package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.CreateTransformerRequest
import co.vendistax.splatcast.models.UpdateTransformerRequest
import co.vendistax.splatcast.services.*
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.transformerRoutes(
    transformerService: TransformerService,
    logger: Logger = LoggerFactory.getLogger("transformerRoutes"),
) {
    route("/apps/{appId}/topics/{topicId}/transformers") {

        post {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()

            try {
                val request = call.receive<CreateTransformerRequest>()
                val transformer = transformerService.createTransform(appId, topicId, request)
                call.respond(HttpStatusCode.Created, transformer)
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: InvalidTransformCodeException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: DuplicateTransformerException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create transformer: app=$appId, topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()

            try {
                val transformers = transformerService.getTransformers(appId, topicId)
                call.respond(HttpStatusCode.OK, transformers)
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve transformers: app=$appId, topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/{transformId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()
            val transformId = call.parameters["transformId"].validateRequired("transformId").toLong()

            try {
                val transformer = transformerService.getTransformer(appId, topicId, transformId)
                call.respond(HttpStatusCode.OK, transformer)
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve transformer=$transformId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/{transformId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()
            val transformId = call.parameters["transformId"].validateRequired("transformId").toLong()

            try {
                val request = call.receive<UpdateTransformerRequest>()
                val transformer = transformerService.updateTransform(appId, topicId, transformId, request)
                call.respond(HttpStatusCode.OK, transformer)
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: InvalidTransformCodeException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update transformer=$transformId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{transformId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()
            val transformId = call.parameters["transformId"].validateRequired("transformId").toLong()

            try {
                transformerService.deleteTransform(appId, topicId, transformId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete transformer=$transformId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}


