package co.vendistax.splatcast.session

import co.vendistax.splatcast.queue.QueueChannel
import co.vendistax.splatcast.queue.implementation.KafkaQueueConsumer
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
    ): SubscriberSessionInterface {
        val queueBusConsumer = KafkaQueueConsumer(channel = QueueChannel(appId, topicId))
        return SubscriberBridgeSession(
            appId = appId,
            topicId = topicId,
            transformerId = transformerId,
            queueBusConsumer = queueBusConsumer,
            serverSession = serverSession,
            transformerService = transformerService,
        )
    }
}