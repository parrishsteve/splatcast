package co.vendistax.splatcast

import co.vendistax.splatcast.plugins.configureDatabase
import co.vendistax.splatcast.plugins.configureRoutes
import co.vendistax.splatcast.plugins.configureSerialization
import co.vendistax.splatcast.plugins.configureStatusPages
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    configureDatabase()
    configureStatusPages()
    configureSerialization()
    configureRoutes()
}
