package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import co.vendistax.splatcast.services.ApiKeyService
import co.vendistax.splatcast.models.*

fun Route.apiKeyRoutes(
    apiKeyService: ApiKeyService,
    logger: Logger = LoggerFactory.getLogger("apiKeyRoutes"),
    ) {
    route("/api/apps/{appId}/keys") {
        get {
            val appId = call.parameters["appId"] ?: throw IllegalArgumentException("Missing appId")
            val keys = apiKeyService.findByAppId(appId)
            call.respond(keys)
        }

        post {
            val appId = call.parameters["appId"] ?: throw IllegalArgumentException("Missing appId")
            val request = call.receive<CreateApiKeyRequest>()
            val response = apiKeyService.create(appId, request)
            call.respond(HttpStatusCode.Created, response)
        }
    }

    route("/api/keys") {
        get("/{keyId}") {
            val keyId = call.parameters["keyId"] ?: throw IllegalArgumentException("Missing keyId")
            val key = apiKeyService.findById(keyId)
                ?: throw NoSuchElementException("API key not found")
            call.respond(key)
        }

        delete("/{keyId}") {
            val keyId = call.parameters["keyId"] ?: throw IllegalArgumentException("Missing keyId")
            apiKeyService.delete(keyId)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/{keyId}/verify") {
            val keyId = call.parameters["keyId"] ?: throw IllegalArgumentException("Missing keyId")
            val request = call.receive<VerifyApiKeyRequest>()
            val isValid = apiKeyService.verify(keyId, request.plainKey)
            call.respond(VerifyApiKeyResponse(isValid))
        }
    }
}

