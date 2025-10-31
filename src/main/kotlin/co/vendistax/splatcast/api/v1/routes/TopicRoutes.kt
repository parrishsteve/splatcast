package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import co.vendistax.splatcast.services.TopicService
import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.validation.validateRequired

fun Route.topicRoutes(
    topicService: TopicService,
    logger: Logger = LoggerFactory.getLogger("topicRoutes"),
) {
    route("/apps/{appId}/topics") {
        get {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val nameFilter = call.request.queryParameters["name"]
            val topics = if (nameFilter != null) {
                topicService.findByAppIdAndName(appId, nameFilter)
            } else {
                topicService.findByAppId(appId)
            }
            call.respond(topics)
        }

        post {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val request = call.receive<CreateTopicRequest>().validate()
            val response = topicService.create(appId, request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()
            val topic = topicService.findById(appId, topicId)
                ?: throw NoSuchElementException("Topic not found")
            call.respond(topic)
        }

        put("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()
            val request = call.receive<UpdateTopicRequest>().validate()
            val response = topicService.update(appId, topicId, request)
            call.respond(response)
        }

        patch("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()
            val request = call.receive<PatchTopicRequest>()

            if (request.defaultSchemaId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "defaultSchemaId is required"))
                return@patch
            }

            val response = topicService.updateDefaultSchema(appId, topicId, request.defaultSchemaId)
            call.respond(response)
        }

        delete("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLong()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLong()
            topicService.delete(appId, topicId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
