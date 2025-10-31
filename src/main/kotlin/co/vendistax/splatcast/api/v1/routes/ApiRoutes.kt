package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.services.ApiKeyService
import co.vendistax.splatcast.services.ApiKeyNotFoundException
import co.vendistax.splatcast.services.AppNotFoundException
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.apiKeyRoutes(
    apiKeyService: ApiKeyService,
    logger: Logger = LoggerFactory.getLogger("apiKeyRoutes"),
) {
    route("/apps/{appId}/keys") {
        get {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@get
            }

            try {
                val keys = apiKeyService.findByAppId(appId)
                call.respond(keys)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve API keys for app with id=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        post {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@post
            }

            try {
                val request = call.receive<CreateApiKeyRequest>()
                val response = apiKeyService.create(appId, request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: AppNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create API key for app with id=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }

    route("/api/keys") {
        get("/{keyId}") {
            val keyId = call.parameters["keyId"].validateRequired("keyId").toLongOrNull()
            if (keyId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid keyId format"))
                return@get
            }

            try {
                val key = apiKeyService.findById(keyId)
                call.respond(key)
            } catch (e: ApiKeyNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve API key with id=$keyId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{keyId}") {
            val keyId = call.parameters["keyId"].validateRequired("keyId").toLongOrNull()
            if (keyId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid keyId format"))
                return@delete
            }

            try {
                apiKeyService.delete(keyId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: ApiKeyNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete API key with id=$keyId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        post("/{keyId}/verify") {
            val keyId = call.parameters["keyId"].validateRequired("keyId").toLongOrNull()
            if (keyId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid keyId format"))
                return@post
            }

            try {
                val request = call.receive<VerifyApiKeyRequest>()
                val isValid = apiKeyService.verify(keyId, request.plainKey)
                call.respond(VerifyApiKeyResponse(isValid))
            } catch (e: ApiKeyNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to verify API key with id=$keyId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}


