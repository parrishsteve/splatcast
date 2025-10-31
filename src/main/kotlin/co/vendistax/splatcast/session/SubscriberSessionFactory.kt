package co.vendistax.splatcast.session

import co.vendistax.splatcast.database.entities.TransformerEntity
import co.vendistax.splatcast.queue.QueueChannel
import co.vendistax.splatcast.queue.implementation.KafkaQueueConsumer
import co.vendistax.splatcast.services.TransformerNotFoundException
import co.vendistax.splatcast.services.TransformerService
import io.ktor.server.websocket.DefaultWebSocketServerSession

class SubscriberSessionFactory(
    private val transformerService: TransformerService,
) {
    @Throws (TransformerNotFoundException::class)
    fun sessionFactory(
        appId: Long,
        topicId: Long,
        transformerId: Long? = null,
        serverSession: DefaultWebSocketServerSession,
    ): SubscriberSessionInterface {
        val channel = QueueChannel(appId.toString(), topicId.toString())
        val queueBusConsumer = KafkaQueueConsumer(channel = channel)
        var transformerEntity: TransformerEntity? = null
        if (transformerId != null) {
            transformerEntity = transformerService.getTransformerEntity(appId, topicId, transformerId)
                ?: throw TransformerNotFoundException("Transformer not found for " +
                        "appId=$appId, topicId=$topicId, transformerId=$transformerId")
        }
        return SubscriberBridgeSession(
            channel = channel,
            transformer = transformerEntity,
            queueBusConsumer = queueBusConsumer,
            serverSession = serverSession,
            transformerService = transformerService,
        )
    }
}