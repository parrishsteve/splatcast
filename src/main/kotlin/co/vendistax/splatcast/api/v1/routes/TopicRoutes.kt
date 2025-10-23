package co.vendistax.splatcast.api.v1.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import co.vendistax.splatcast.services.TopicService
import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.validation.validateRequired

fun Route.topicRoutes(topicService: TopicService) {
    route("/api/apps/{appId}/topics") {
        get {
            val appId = call.parameters["appId"]?.validateRequired("appId")!!
            val topics = topicService.findByAppId(appId)
            call.respond(topics)
        }

        post {
            val appId = call.parameters["appId"]?.validateRequired("appId")!!
            val request = call.receive<CreateTopicRequest>().validate()
            val response = topicService.create(appId, request)
            call.respond(HttpStatusCode.Created, response)
        }

        get("/{topicId}") {
            val appId = call.parameters["appId"]?.validateRequired("appId")!!
            val topicId = call.parameters["topicId"]?.validateRequired("topicId")!!
            val topic = topicService.findById(appId, topicId)
                ?: throw NoSuchElementException("Topic not found")
            call.respond(topic)
        }

        put("/{topicId}") {
            val appId = call.parameters["appId"]?.validateRequired("appId")!!
            val topicId = call.parameters["topicId"]?.validateRequired("topicId")!!
            val request = call.receive<UpdateTopicRequest>().validate()
            val response = topicService.update(appId, topicId, request)
            call.respond(response)
        }

        delete("/{topicId}") {
            val appId = call.parameters["appId"]?.validateRequired("appId")!!
            val topicId = call.parameters["topicId"]?.validateRequired("topicId")!!
            topicService.delete(appId, topicId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
