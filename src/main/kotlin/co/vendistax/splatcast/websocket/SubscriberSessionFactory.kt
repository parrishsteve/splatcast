package co.vendistax.splatcast.websocket

import co.vendistax.splatcast.queue.implementation.KafkaQueueBus
import co.vendistax.splatcast.services.TransformerService
import io.ktor.server.websocket.DefaultWebSocketServerSession

class SubscriberSessionFactory(
    private val transformerService: TransformerService,
) {
    fun sessionFactory(
        appId: String,
        topicId: String,
        transformerId: String,
        serverSession: DefaultWebSocketServerSession,
    ): SubscriberSession {
        val queueBus = KafkaQueueBus()
        return SubscriberSession(
            appId = appId,
            topicId = topicId,
            transformerId = transformerId,
            queueBus = queueBus,
            serverSession = serverSession,
            transformerService = transformerService,
        )
    }
}