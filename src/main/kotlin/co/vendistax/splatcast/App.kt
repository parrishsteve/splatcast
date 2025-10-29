package co.vendistax.splatcast

import co.vendistax.splatcast.plugins.configureDatabase
import co.vendistax.splatcast.plugins.configureRoutes
import co.vendistax.splatcast.plugins.configureSerialization
import co.vendistax.splatcast.plugins.configureStatusPages
import co.vendistax.splatcast.plugins.configureWebsockets
import co.vendistax.splatcast.queue.implementation.KafkaQueueProducer
import co.vendistax.splatcast.services.ApiKeyService
import co.vendistax.splatcast.services.AppService
import co.vendistax.splatcast.services.AuditService
import co.vendistax.splatcast.services.JavaScriptRuntimeService
import co.vendistax.splatcast.services.PublishingService
import co.vendistax.splatcast.services.SchemaService
import co.vendistax.splatcast.services.ServiceDependencies
import co.vendistax.splatcast.services.TopicService
import co.vendistax.splatcast.services.TransformerService
import co.vendistax.splatcast.session.SubscriberSessionFactory
import co.vendistax.splatcast.session.SubscriberSessionHub
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080) {
        val services = serviceModule()
        module(serviceDependencies = services)
    }.start(wait = true)
}

fun Application.serviceModule(): ServiceDependencies  {
    val jsRuntime = JavaScriptRuntimeService()
    val transformerService = TransformerService(jsRuntime)
    val subscriberSessionFactory = SubscriberSessionFactory(transformerService)
    val subscriberSessionHub = SubscriberSessionHub(subscriberSessionFactory = subscriberSessionFactory)
    val queueProducer = KafkaQueueProducer()

    return ServiceDependencies (
        appService = AppService(),
        apiKeyService = ApiKeyService(),
        topicService = TopicService(),
        auditService = AuditService(),
        schemaService = SchemaService(),
        transformerService = transformerService,
        publishingService = PublishingService(transformerService = transformerService, queueBusProducer = queueProducer),
        subscriberSessionHub = subscriberSessionHub,
    )
}

fun Application.module(serviceDependencies: ServiceDependencies) {
    configureDatabase()
    configureStatusPages()
    configureSerialization()
    configureWebsockets()
    configureRoutes(serviceDependencies)
}
