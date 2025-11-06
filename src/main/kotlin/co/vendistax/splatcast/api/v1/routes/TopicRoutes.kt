package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.Config
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.*
import co.vendistax.splatcast.services.AppService
import co.vendistax.splatcast.services.SchemaNotFoundException
import co.vendistax.splatcast.services.TopicService
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.topicRoutes(
    appService: AppService,
    topicService: TopicService,
    logger: Logger = LoggerFactory.getLogger("topicRoutes"),
) {
    // ID-based routes: /apps/{appId}/topics
    route("${Config.BASE_URL}/apps/{appId}/topics") {
        get {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            if (appId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid appId format"))
                return@get
            }

            try {
                val nameFilter = call.request.queryParameters["name"]
                val topics = if (nameFilter != null) {
                    topicService.findByAppIdAndName(appId, nameFilter)
                } else {
                    topicService.findByAppId(appId)
                }
                call.respond(topics)
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve topics for appId=$appId")
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
                val request = call.receive<CreateTopicRequest>().validate()
                val response = topicService.create(appId, request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: SchemaNotFoundException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create topic for appId=$appId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            if (appId == null || topicId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@get
            }

            try {
                val topic = topicService.findById(appId, topicId)
                call.respond(topic)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            if (appId == null || topicId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@put
            }

            try {
                val request = call.receive<UpdateTopicRequest>().validate()
                val response = topicService.update(appId, topicId, request)
                call.respond(response)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        patch("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            if (appId == null || topicId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@patch
            }

            try {
                val request = call.receive<PatchTopicRequest>()
                val response = topicService.patch(appId, topicId, request)
                call.respond(response)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to patch topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{topicId}") {
            val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
            val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
            if (appId == null || topicId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID format"))
                return@delete
            }

            try {
                topicService.delete(appId, topicId)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete topic=$topicId")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }

    // Name-based routes: /apps/by-name/{appName}/topics
    route("${Config.BASE_URL}/apps/${Config.NAME_URL_PREFACE}/{appName}/topics") {
        get {
            val appName = call.parameters["appName"].validateRequired("appName")

            try {
                val app = appService.findByName(appName)
                val nameFilter = call.request.queryParameters["name"]
                val topics = if (nameFilter != null) {
                    topicService.findByAppIdAndName(app.appId, nameFilter)
                } else {
                    topicService.findByAppId(app.appId)
                }
                call.respond(topics)
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve topics for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        post {
            val appName = call.parameters["appName"].validateRequired("appName")

            try {
                val app = appService.findByName(appName)
                val request = call.receive<CreateTopicRequest>().validate()
                val response = topicService.create(app.appId, request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to create topic for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        get("/{topicName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val app = appService.findByName(appName)
                val topicsResponse = topicService.findByAppIdAndName(app.appId, topicName)
                call.respond(topicsResponse)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to retrieve topic=$topicName for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        put("/{topicName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val app = appService.findByName(appName)
                val request = call.receive<UpdateTopicRequest>().validate()
                val response = topicService.update(app.appId, topicName, request)
                call.respond(response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to update topic=$topicName for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        patch("/{topicName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val app = appService.findByName(appName)
                val request = call.receive<PatchTopicRequest>()
                val response = topicService.patch(app.appId, topicName, request)
                call.respond(response)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to patch topic=$topicName for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        delete("/{topicName}") {
            val appName = call.parameters["appName"].validateRequired("appName")
            val topicName = call.parameters["topicName"].validateRequired("topicName")

            try {
                val app = appService.findByName(appName)
                topicService.delete(app.appId, topicName)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: NoSuchElementException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: Exception) {
                logger.error(e, "Failed to delete topic=$topicName for app=$appName")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}

