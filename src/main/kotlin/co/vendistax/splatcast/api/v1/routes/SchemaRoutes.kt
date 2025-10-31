package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.CreateSchemaRequest
import co.vendistax.splatcast.models.UpdateSchemaRequest
import co.vendistax.splatcast.services.*
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.schemaRoutes(
    schemaService: SchemaService,
    logger: Logger = LoggerFactory.getLogger("schemaRoutes"),
) {
    route("/apps/{appId}/schemas") {

        post {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()

            try {
                val request = call.receive<CreateSchemaRequest>()
                val schema = schemaService.createSchema(appId, request)
                call.respond(HttpStatusCode.Created, schema)
            } catch (e: InvalidSchemaException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create schema for appId=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()

            try {
                val status = call.request.queryParameters["status"]?.let {
                    co.vendistax.splatcast.database.tables.SchemaStatus.fromString(it)
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val schemas = schemaService.getSchemas(appId, status, limit, offset)
                call.respond(HttpStatusCode.OK, schemas)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve schemas for appId=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/{schemaId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val schemaId = call.parameters["schemaId"].validateRequired("schemaId").toLong()

            try {
                val schema = schemaService.getSchema(appId, schemaId)
                call.respond(HttpStatusCode.OK, schema)
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve schema=$schemaId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/name/{name}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val name = call.parameters["name"].validateRequired("name")

            try {
                val schema = schemaService.getSchemaByName(appId, name)
                call.respond(HttpStatusCode.OK, schema)
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve schema with name=$name")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/{schemaId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val schemaId = call.parameters["schemaId"].validateRequired("schemaId").toLong()

            try {
                val request = call.receive<UpdateSchemaRequest>()
                val schema = schemaService.updateSchema(appId, schemaId, request)
                call.respond(HttpStatusCode.OK, schema)
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: SchemaStatusTransitionException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update schema=$schemaId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{schemaId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val schemaId = call.parameters["schemaId"].validateRequired("schemaId").toLong()

            try {
                schemaService.deleteSchema(appId, schemaId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete schema=$schemaId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}


