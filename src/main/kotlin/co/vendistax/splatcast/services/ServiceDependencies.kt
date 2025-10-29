package co.vendistax.splatcast.services

import co.vendistax.splatcast.session.SubscriberSessionHub

data class ServiceDependencies(
    val appService: AppService,
    val apiKeyService: ApiKeyService,
    val topicService: TopicService,
    val auditService: AuditService,
    val schemaService: SchemaService,
    val transformerService: TransformerService,
    val publishingService: PublishingService,
    val subscriberSessionHub: SubscriberSessionHub,
)