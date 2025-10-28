package co.vendistax.splatcast.plugins
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

fun Application.configureWebsockets() {
    // WebSocket configuration can be added here if needed in the future
    install(WebSockets)
}