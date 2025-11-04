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
        topicRoutes(
            appService = serviceDependencies.appService,
            topicService = serviceDependencies.topicService)
        auditRoutes(serviceDependencies.auditService)
        schemaRoutes(
            appService = serviceDependencies.appService,
            schemaService = serviceDependencies.schemaService)
        transformerRoutes(
            appService = serviceDependencies.appService,
            transformerService = serviceDependencies.transformerService)
        eventPublishingRoutes(
            appService = serviceDependencies.appService,
            eventPublishingService = serviceDependencies.publishingService)
        webSocketRoutes(
            appService = serviceDependencies.appService,
            subscriberSessionHub = serviceDependencies.subscriberSessionHub)
    }
}