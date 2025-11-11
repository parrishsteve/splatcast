package co.vendistax.splatcast.session

import co.vendistax.splatcast.queue.QueueChannel
import co.vendistax.splatcast.queue.implementation.KafkaQueueConsumer
import co.vendistax.splatcast.services.TopicService
import co.vendistax.splatcast.services.TransformerNotFoundException
import co.vendistax.splatcast.services.TransformerService
import io.ktor.server.websocket.DefaultWebSocketServerSession

class SubscriberSessionFactory(
    private val topicService: TopicService,
    private val transformerService: TransformerService,
) {
    @Throws (TransformerNotFoundException::class)
    fun sessionFactory(
        appId: Long,
        topicId: Long,
        toSchemaId: Long? = null,
        fromTimestamp: Long? = null,
        serverSession: DefaultWebSocketServerSession,
    ): SubscriberSessionInterface {
        val topicEntity = topicService.getTopicEntityById(appId, topicId)
        val channel = QueueChannel(appId.toString(), topicId.toString())
        val queueBusConsumer = KafkaQueueConsumer(channel = channel, fromTimestamp = fromTimestamp)
        val transformerEntity = toSchemaId?.let {
            transformerService.getTransformerEntityFromTopic(topicEntity, it)
        }
        return SubscriberBridgeSession(
            channel = channel,
            transformer = transformerEntity,
            queueBusConsumer = queueBusConsumer,
            serverSession = serverSession,
            transformerService = transformerService,
        )
    }

    @Throws (TransformerNotFoundException::class)
    fun sessionFactory(
        appId: Long,
        topicId: Long,
        toSchemaName: String? = null,
        fromTimestamp: Long? = null,
        serverSession: DefaultWebSocketServerSession,
    ): SubscriberSessionInterface {
        val topicEntity = topicService.getTopicEntityById(appId, topicId)
        val channel = QueueChannel(appId.toString(), topicId.toString())
        val queueBusConsumer = KafkaQueueConsumer(channel = channel, fromTimestamp = fromTimestamp)
        val transformerEntity = toSchemaName?.takeIf { it.isNotEmpty() }?.let {
            transformerService.getTransformerEntityFromTopic(topicEntity, it)
        }
        return SubscriberBridgeSession(
            channel = channel,
            transformer = transformerEntity,
            queueBusConsumer = queueBusConsumer,
            serverSession = serverSession,
            transformerService = transformerService,
        )
    }

    @Throws (TransformerNotFoundException::class)
    fun sessionFactory(
        appId: Long,
        topicName: String,
        toSchemaId: Long? = null,
        fromTimestamp: Long? = null,
        serverSession: DefaultWebSocketServerSession,
    ): SubscriberSessionInterface {
        val topicEntity = topicService.getTopicEntityByName(appId, topicName)
        val channel = QueueChannel(appId.toString(), topicEntity.id.value.toString())
        val queueBusConsumer = KafkaQueueConsumer(channel = channel, fromTimestamp = fromTimestamp)
        val transformerEntity = toSchemaId?.let {
            transformerService.getTransformerEntityFromTopic(topicEntity, it)
        }
        return SubscriberBridgeSession(
            channel = channel,
            transformer = transformerEntity,
            queueBusConsumer = queueBusConsumer,
            serverSession = serverSession,
            transformerService = transformerService,
        )
    }

    @Throws (TransformerNotFoundException::class)
    fun sessionFactory(
        appId: Long,
        topicName: String,
        toSchemaName: String? = null,
        fromTimestamp: Long? = null,
        serverSession: DefaultWebSocketServerSession,
    ): SubscriberSessionInterface {
        val topicEntity = topicService.getTopicEntityByName(appId, topicName)
        val channel = QueueChannel(appId.toString(), topicEntity.id.value.toString())
        val queueBusConsumer = KafkaQueueConsumer(channel = channel, fromTimestamp = fromTimestamp)
        val transformerEntity = toSchemaName?.takeIf { it.isNotEmpty() }?.let {
            transformerService.getTransformerEntityFromTopic(topicEntity, it)
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