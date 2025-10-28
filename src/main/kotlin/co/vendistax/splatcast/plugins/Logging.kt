package co.vendistax.splatcast.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install


fun Application.configureLogging() {
    // Configure logging here if needed for HTTP requests and responses
    /*install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }*/
}