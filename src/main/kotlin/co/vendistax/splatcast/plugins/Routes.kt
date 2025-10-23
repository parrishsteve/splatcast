package co.vendistax.splatcast.plugins

import co.vendistax.splatcast.api.v1.routes.apiKeyRoutes
import co.vendistax.splatcast.api.v1.routes.appRoutes
import co.vendistax.splatcast.api.v1.routes.auditRoutes
import co.vendistax.splatcast.api.v1.routes.schemaRoutes
import co.vendistax.splatcast.api.v1.routes.topicRoutes
import co.vendistax.splatcast.api.v1.routes.transformerRoutes
import co.vendistax.splatcast.services.ApiKeyService
import co.vendistax.splatcast.services.AppService
import co.vendistax.splatcast.services.AuditService
import co.vendistax.splatcast.services.SchemaService
import co.vendistax.splatcast.services.TopicService
import co.vendistax.splatcast.services.TransformerService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRoutes() {
    // Initialize services
    val appService = AppService()
    val apiKeyService = ApiKeyService()
    val topicService = TopicService()
    val auditService = AuditService()
    val schemaService = SchemaService()
    val transformerService = TransformerService()
    // ... other services

    // Configure routes
    routing {
        appRoutes(appService)
        apiKeyRoutes(apiKeyService)
        topicRoutes(topicService)
        auditRoutes(auditService)
        schemaRoutes(schemaService)
        transformerRoutes(transformerService)
        // ... other routes
    }
}