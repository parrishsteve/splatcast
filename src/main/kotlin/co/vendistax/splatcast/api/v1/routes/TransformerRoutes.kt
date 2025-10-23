package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.models.CreateTransformerRequest
import co.vendistax.splatcast.models.UpdateTransformerRequest
import co.vendistax.splatcast.services.TransformerService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.transformerRoutes(transformerService: TransformerService) {
    route("/apps/{appId}/topics/{topicId}/transforms") {

        // POST /apps/{appId}/topics/{topicId}/transforms - Create transform
        post {
            val appId = call.parameters["appId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing topicId")

            val request = call.receive<CreateTransformerRequest>()

            transformerService.createTransform(appId, topicId, request)
                .onSuccess { transform -> call.respond(HttpStatusCode.Created, transform) }
                .onFailure { error ->
                    when {
                        error.message?.contains("already exists") == true ->
                            call.respond(HttpStatusCode.Conflict, mapOf("error" to error.message))
                        error.message?.contains("not found") == true ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                    }
                }
        }

        // GET /apps/{appId}/topics/{topicId}/transforms - Get all transforms for topic
        get {
            val appId = call.parameters["appId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing topicId")

            transformerService.getTransforms(appId, topicId)
                .onSuccess { transforms -> call.respond(HttpStatusCode.OK, transforms) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error")) }
        }

        // GET /apps/{appId}/topics/{topicId}/transforms/{transformId} - Get specific transform
        get("/{transformId}") {
            val appId = call.parameters["appId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing topicId")
            val transformId = call.parameters["transformId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing transformId")

            transformerService.getTransform(appId, topicId, transformId)
                .onSuccess { transform -> call.respond(HttpStatusCode.OK, transform) }
                .onFailure { error ->
                    when {
                        error.message?.contains("not found") == true ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                    }
                }
        }

        // PUT /apps/{appId}/topics/{topicId}/transforms/{transformId} - Update transform
        put("/{transformId}") {
            val appId = call.parameters["appId"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing topicId")
            val transformId = call.parameters["transformId"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing transformId")

            val request = call.receive<UpdateTransformerRequest>()

            transformerService.updateTransform(appId, topicId, transformId, request)
                .onSuccess { transform -> call.respond(HttpStatusCode.OK, transform) }
                .onFailure { error ->
                    when {
                        error.message?.contains("not found") == true ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                    }
                }
        }

        // DELETE /apps/{appId}/topics/{topicId}/transforms/{transformId} - Delete transform
        delete("/{transformId}") {
            val appId = call.parameters["appId"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing topicId")
            val transformId = call.parameters["transformId"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing transformId")

            transformerService.deleteTransform(appId, topicId, transformId)
                .onSuccess { call.respond(HttpStatusCode.NoContent) }
                .onFailure { error ->
                    when {
                        error.message?.contains("not found") == true ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                    }
                }
        }
    }
}
