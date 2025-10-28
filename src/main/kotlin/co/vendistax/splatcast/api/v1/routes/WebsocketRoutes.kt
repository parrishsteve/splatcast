package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.websocket.TopicWebSocketHub
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException

fun Route.webSocketRoutes(
    topicWebSocketHub: TopicWebSocketHub,
    logger: Logger = LoggerFactory.getLogger("webSocketRoutes"),
    ) {
    webSocket("/apps/{appId}/topics/{topicId}/subscribe") {
        val appId = call.parameters["appId"] ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "missing appId"))
        val topicId = call.parameters["topicId"] ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "missing topicId"))

        // Optionally validate Authorization header here: call.request.headers["Authorization"]
        topicWebSocketHub.add(appId, topicId, "trf_1761415449642", this as DefaultWebSocketServerSession)
        try {
            for (frame in incoming) {
                if (frame is Frame.Close) break
                // ignore other incoming frames or implement ping/commands
            }
        } catch (_: ClosedReceiveChannelException) {
        } finally {
            topicWebSocketHub.destroy(appId, topicId, this as DefaultWebSocketServerSession)
        }
    }
}