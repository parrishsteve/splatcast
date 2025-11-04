package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.services.AppService
import co.vendistax.splatcast.services.TransformerNotFoundException
import co.vendistax.splatcast.services.TransformerService
import co.vendistax.splatcast.session.SubscriberSessionHub
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlin.text.get

fun Route.webSocketRoutes(
    appService: AppService,
    subscriberSessionHub: SubscriberSessionHub,
    logger: Logger = LoggerFactory.getLogger("webSocketRoutes"),
) {
    webSocket("/apps/{appId}/topics/{topicId}/subscribe") {
        val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
        val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()
        if (appId == null || topicId == null) {
            this.close(CloseReason(4000, "Invalid appId or topicId"))
            return@webSocket
        }

        var sessionId: String? = null
        try {
            // Extract optional transformer/schema configuration
            val targetSchemaId = call.request.queryParameters["schemaId"]?.toLongOrNull()
            val targetSchemaName = call.request.queryParameters["schemaName"]

            // Register session with transformer preference
            sessionId = subscriberSessionHub.add(
                appId = appId,
                topicId = topicId,
                schemaId = targetSchemaId,
                schemaName = targetSchemaName,
                session = this as DefaultWebSocketServerSession
            )

            logger.info {
                "WebSocket connected: session=$sessionId, app=$appId, topic=$topicId, " +
                        "schemaId=$targetSchemaId, schemaName=$targetSchemaName"
            }

            for (frame in incoming) {
                when (frame) {
                    is Frame.Close -> break
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    is Frame.Text -> {
                        val text = frame.readText()
                        logger.warn { "Unexpected message from client: $text" }
                    }
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.debug { "WebSocket closed normally" }
        } catch (e: TransformerNotFoundException) {
            // This would be thrown by the session factory
            this.close(CloseReason (code = 4004, message = e.message ?: "Transformer not found"))
            logger.error(e, "Transformer not found: app=$appId, topic=$topicId")
        }
        catch (e: Exception) {
            logger.error(e, "WebSocket error: app=$appId, topic=$topicId")
        } finally {
            sessionId?.let(subscriberSessionHub::destroy)
        }
    }
    webSocket("/apps/by-name/{appName}/topics/{topicName}/subscribe") {
        val appName = call.parameters["appName"].validateRequired("appName")
        val topicName = call.parameters["topicName"].validateRequired("topicName")

        var sessionId: String? = null
        try {
            // Extract optional transformer/schema configuration
            val targetSchemaId = call.request.queryParameters["schemaId"]?.toLongOrNull()
            val targetSchemaName = call.request.queryParameters["schemaName"]
            val app = appService.findByName(appName)

            sessionId = subscriberSessionHub.add(
                appId = app.appId,
                topicName = topicName,
                schemaId = targetSchemaId,
                schemaName = targetSchemaName,
                session = this as DefaultWebSocketServerSession
            )

            logger.info {
                "WebSocket connected by name: session=$sessionId, appName=$appName, topicName=$topicName, " +
                        "schemaId=$targetSchemaId, schemaName=$targetSchemaName"
            }

            for (frame in incoming) {
                when (frame) {
                    is Frame.Close -> break
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    is Frame.Text -> {
                        val text = frame.readText()
                        logger.warn { "Unexpected message from client: $text" }
                    }
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.debug { "WebSocket closed normally" }
        } catch (e: Exception) {
            logger.error(e, "WebSocket error: appName=$appName, topicName=$topicName")
        } finally {
            sessionId?.let(subscriberSessionHub::destroy)
        }
    }
}
