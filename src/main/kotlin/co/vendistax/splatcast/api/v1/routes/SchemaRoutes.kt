package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.CreateSchemaRequest
import co.vendistax.splatcast.models.UpdateSchemaRequest
import co.vendistax.splatcast.services.SchemaService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.schemaRoutes(
    schemaService: SchemaService,
    logger: Logger = LoggerFactory.getLogger("schemaRoutes"),
){
    route("/apps/{appId}/topics/{topicId}/schemas") {

        // POST /apps/{appId}/topics/{topicId}/schemas - Create schema
        post {
            val appId = call.parameters["appId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing topicId")

            val request = call.receive<CreateSchemaRequest>()

            schemaService.createSchema(appId, topicId, request)
                .onSuccess { schema -> call.respond(HttpStatusCode.Created, schema) }
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

        // GET /apps/{appId}/topics/{topicId}/schemas - Get all schemas for topic
        get {
            val appId = call.parameters["appId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing topicId")

            schemaService.getSchemas(appId, topicId)
                .onSuccess { schemas -> call.respond(HttpStatusCode.OK, schemas) }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error")) }
        }

        // GET /apps/{appId}/topics/{topicId}/schemas/{schemaId} - Get specific schema
        get("/{schemaId}") {
            val appId = call.parameters["appId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing topicId")
            val schemaId = call.parameters["schemaId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing schemaId")

            schemaService.getSchema(appId, topicId, schemaId)
                .onSuccess { schema -> call.respond(HttpStatusCode.OK, schema) }
                .onFailure { error ->
                    when {
                        error.message?.contains("not found") == true ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
                    }
                }
        }

        // PUT /apps/{appId}/topics/{topicId}/schemas/{schemaId} - Update schema status
        put("/{schemaId}") {
            val appId = call.parameters["appId"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing appId")
            val topicId = call.parameters["topicId"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing topicId")
            val schemaId = call.parameters["schemaId"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing schemaId")

            val request = call.receive<UpdateSchemaRequest>()

            schemaService.updateSchema(appId, topicId, schemaId, request)
                .onSuccess { schema -> call.respond(HttpStatusCode.OK, schema) }
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
