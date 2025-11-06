package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.Config
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
    appService: AppService,
    transformerService: TransformerService,
    logger: Logger = LoggerFactory.getLogger("transformerRoutes"),
) {
    // ID-based routes: /apps/{appId}/topics/{topicId}/transformers
    route("${Config.BASE_URL}/apps/{appId}/topics/{topicId}/transformers") {

        post {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            if (appId == null || topicId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@post
            }

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
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            if (appId == null || topicId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@get
            }

            try {
                val transformers = transformerService.getTransformers(appId, topicId)
                call.respond(HttpStatusCode.OK, transformers)
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve transformers: app=$appId, topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/{transformId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            val transformId = call.parameters["transformId"].validateRequired("transformId").toLongOrNull()
            if (appId == null || topicId == null || transformId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@get
            }

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
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            val transformId = call.parameters["transformId"].validateRequired("transformId").toLongOrNull()
            if (appId == null || topicId == null || transformId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@put
            }

            try {
                val request = call.receive<UpdateTransformerRequest>()
                val transformer = transformerService.updateTransformer(appId, topicId, transformId, request)
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
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            val transformId = call.parameters["transformId"].validateRequired("transformId").toLongOrNull()
            if (appId == null || topicId == null || transformId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@delete
            }

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

    // Name-based routes: /apps/by-name/{appName}/topics/{topicName}/transformers
    route("${Config.BASE_URL}/apps/${Config.NAME_URL_PREFACE}/{appName}/topics/{topicName}/transformers") {

        post {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val app = appService.findByName(appName)
                val request = call.receive<CreateTransformerRequest>()
                val transformer = transformerService.createTransform(app.appId, topicName, request)
                call.respond(HttpStatusCode.Created, transformer)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: InvalidTransformCodeException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: DuplicateTransformerException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create transformer: app=$appName, topic=$topicName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val app = appService.findByName(appName)
                val transformers = transformerService.getTransformers(app.appId, topicName)
                call.respond(HttpStatusCode.OK, transformers)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve transformers: app=$appName, topic=$topicName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/{transformerName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")
            val transformerName = call.parameters["transformerName"].validateRequired("transformerName")

            try {
                val app = appService.findByName(appName)
                val transformer = transformerService.getTransformer(app.appId, topicName, transformerName)
                call.respond(HttpStatusCode.OK, transformer)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: TransformerNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            }
            catch (e: Exception) {
                logger.error(e, "Failed to retrieve transformer=$transformerName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/{transformerName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")
            val transformerName = call.parameters["transformerName"].validateRequired("transformerName")

            try {
                val app = appService.findByName(appName)
                val request = call.receive<UpdateTransformerRequest>()
                val updated = transformerService.updateTransformer(app.appId, topicName, transformerName, request)
                call.respond(HttpStatusCode.OK, updated)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: InvalidTransformCodeException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update transformer=$transformerName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{transformerName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")
            val transformerName = call.parameters["transformerName"].validateRequired("transformerName")

            try {
                val app = appService.findByName(appName)
                transformerService.deleteTransform(app.appId, topicName, transformerName)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: TransformerNotFoundException) {
                logger.error(e, "Transformer not found: transformer=$transformerName")
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete transformer=$transformerName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}
