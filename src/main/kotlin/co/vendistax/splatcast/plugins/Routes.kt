package co.vendistax.splatcast.plugins

import co.vendistax.splatcast.api.v1.routes.apiKeyRoutes
import co.vendistax.splatcast.api.v1.routes.appRoutes
import co.vendistax.splatcast.api.v1.routes.auditRoutes
import co.vendistax.splatcast.api.v1.routes.eventPublishingRoutes
import co.vendistax.splatcast.api.v1.routes.schemaRoutes
import co.vendistax.splatcast.api.v1.routes.topicRoutes
import co.vendistax.splatcast.api.v1.routes.transformerRoutes
import co.vendistax.splatcast.api.v1.routes.webSocketRoutes
import co.vendistax.splatcast.services.ServiceDependencies
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRoutes(
    serviceDependencies: ServiceDependencies
) {
    routing {
        appRoutes(serviceDependencies.appService)
        apiKeyRoutes(serviceDependencies.apiKeyService)
        topicRoutes(serviceDependencies.topicService)
        auditRoutes(serviceDependencies.auditService)
        schemaRoutes(serviceDependencies.schemaService)
        transformerRoutes(serviceDependencies.transformerService)
        eventPublishingRoutes(serviceDependencies.publishingService)
        webSocketRoutes(serviceDependencies.topicWebSocketHub)
    }
}