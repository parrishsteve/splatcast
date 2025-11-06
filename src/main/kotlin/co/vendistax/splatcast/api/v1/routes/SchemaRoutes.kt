package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.Config
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
    appService: AppService,
    schemaService: SchemaService,
    logger: Logger = LoggerFactory.getLogger("schemaRoutes"),
) {
    // ID-based routes: /apps/{appId}/schemas
    route("${Config.BASE_URL}/apps/{appId}/schemas") {
        post {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@post
            }

            try {
                val request = call.receive<CreateSchemaRequest>().validate()
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
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@get
            }

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
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val schemaId = call.parameters["schemaId"].validateRequired("schemaId").toLongOrNull()
            if (appId == null || schemaId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@get
            }

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

        put("/{schemaId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val schemaId = call.parameters["schemaId"].validateRequired("schemaId").toLongOrNull()
            if (appId == null || schemaId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@put
            }

            try {
                val request = call.receive<UpdateSchemaRequest>().validate()
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
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val schemaId = call.parameters["schemaId"].validateRequired("schemaId").toLongOrNull()
            if (appId == null || schemaId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@delete
            }

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

    // Name-based routes: /apps/by-name/{appName}/schemas
    route("${Config.BASE_URL}/apps/${Config.NAME_URL_PREFACE}/{appName}/schemas") {
        post {
            val appName = call.parameters["appName"].validateRequired("appName")

            try {
                val app = appService.findByName(appName)
                val request = call.receive<CreateSchemaRequest>().validate()
                val schema = schemaService.createSchema(app.appId, request)
                call.respond(HttpStatusCode.Created, schema)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: InvalidSchemaException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create schema for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get {
            val appName = call.parameters["appName"].validateRequired("appName")

            try {
                val app = appService.findByName(appName)
                val status = call.request.queryParameters["status"]?.let {
                    co.vendistax.splatcast.database.tables.SchemaStatus.fromString(it)
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val schemas = schemaService.getSchemas(app.appId, status, limit, offset)
                call.respond(HttpStatusCode.OK, schemas)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve schemas for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/{schemaName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val schemaName = call.parameters["schemaName"].validateRequired("schemaName")

            try {
                val app = appService.findByName(appName)
                val schema = schemaService.getSchemaByName(app.appId, schemaName)
                call.respond(HttpStatusCode.OK, schema)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve schema=$schemaName for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/{schemaName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val schemaName = call.parameters["schemaName"].validateRequired("schemaName")

            try {
                val app = appService.findByName(appName)
                val existingSchema = schemaService.getSchemaByName(app.appId, schemaName)
                val request = call.receive<UpdateSchemaRequest>().validate()
                val schema = schemaService.updateSchema(app.appId, existingSchema.id, request)
                call.respond(HttpStatusCode.OK, schema)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: SchemaStatusTransitionException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update schema=$schemaName for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{schemaName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val schemaName = call.parameters["schemaName"].validateRequired("schemaName")

            try {
                val app = appService.findByName(appName)
                val existingSchema = schemaService.getSchemaByName(app.appId, schemaName)
                schemaService.deleteSchema(app.appId, existingSchema.id)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalStateException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete schema=$schemaName for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}
