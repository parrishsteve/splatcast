package co.vendistax.splatcast.plugins
import co.vendistax.splatcast.Config
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebsockets() {
    // WebSocket configuration can be added here if needed in the future
    install(WebSockets) {
        pingPeriod = Config.WEBSOCKET_PING_INTERVAL_SECS.seconds
        timeout = Config.WEBSOCKET_TIMEOUT_SECS.seconds
        maxFrameSize = Config.MAX_WEBSOCKET_FRAME_SIZE
        masking = false
    }
}