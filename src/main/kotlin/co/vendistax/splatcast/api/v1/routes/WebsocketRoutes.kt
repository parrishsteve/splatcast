package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.session.SubscriberSessionHub
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException

fun Route.webSocketRoutes(
    subscriberSessionHub: SubscriberSessionHub,
    logger: Logger = LoggerFactory.getLogger("webSocketRoutes"),
    ) {
    webSocket("/apps/{appId}/topics/{topicId}/subscribe") {
        val appId = call.parameters["appId"] ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "missing appId"))
        val topicId = call.parameters["topicId"] ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "missing topicId"))
        // Optionally validate Authorization header here: call.request.headers["Authorization"]
        val sessionId = subscriberSessionHub.add(appId, topicId, "trf_1761415449642", this as DefaultWebSocketServerSession)
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Close -> break  // Client wants to disconnect
                    is Frame.Ping -> send(Frame.Pong(frame.data))  // Respond to keepalive
                    is Frame.Text -> {  // Client sent text data
                        val text = frame.readText()
                        logger.info { "Received from client, this should not happen: $text" }
                    }
                    else -> {}  // Ignore other frames
                }
            }
        } catch (_: ClosedReceiveChannelException) {
        } finally {
            subscriberSessionHub.destroy(sessionId)
        }
    }
}