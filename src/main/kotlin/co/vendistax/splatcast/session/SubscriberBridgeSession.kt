package co.vendistax.splatcast.session

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.queue.QueueBusConsumer
import co.vendistax.splatcast.services.TransformerService
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SubscriberBridgeSession(
    private val appId: String,
    private val topicId: String,
    private val transformerId: String?,
    private val queueBusConsumer: QueueBusConsumer,
    private val serverSession: DefaultWebSocketServerSession,
    private val transformerService: TransformerService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val logger: Logger = LoggerFactory.getLogger<SubscriberBridgeSession>(),
): SubscriberSessionInterface {

    override fun start() {
        logger.info { "Starting subscriber session for appId=$appId, topicId=$topicId, transformerId=$transformerId" }
        queueBusConsumer.start { channel, message ->
            logger.info { "Received Kafka event for channel=$channel, message=$message" }
            scope.launch {
                logger.info { "Now processing Kafka event for channel=$channel, message=$message" }
                try {
                    // If there's a transformerId, use it to transform the message
                    if (!transformerId.isNullOrEmpty()) {
                        val transformedMessage = executeTransformer(transformerId = transformerId, message = message)
                        publish(transformedMessage)
                    } else {
                        logger.info { "No transformation needed for event message=$message" }
                        publish(message)
                    }
                } catch (ex: Throwable) {
                    logger.error(ex) { "Error processing message for channel=$channel" }
                }
            }
        }
    }

    override fun stop() {
        logger.info { "Stopping subscriber session for appId=$appId, topicId=$topicId, transformerId=$transformerId" }
        scope.launch {
            try {
                queueBusConsumer.stop()
                serverSession.close(reason = CloseReason(code = 5000, message = "Session closed"))
            } catch (ex: Throwable) {
                logger.error(ex) { "Error closing subscriber session for appId=$appId, topicId=$topicId" }
            }
        }
    }

    private fun executeTransformer(transformerId: String, message: String): String {
        logger.info { "Need to transform event message=$message" }
        val jsonObject = Json.parseToJsonElement(message).jsonObject
        val result = transformerService.executeTransform(transformerId, jsonObject)
        val ret = result.getOrNull()
        if (result.isSuccess && ret != null) {
            return ret.transformedData.toString()
        }
        val originalException = result.exceptionOrNull() ?: Exception("Unknown error")
        logger.error(originalException) {
            "Error transforming message with transformerId=$transformerId"
        }
        throw Exception("Transformation failed for transformerId=$transformerId", originalException)
    }

    private suspend fun publish(message: String) {
        try {
            logger.info { "Publishing: $message" }
            serverSession.send(Frame.Text(message))
        } catch (ex: Throwable) {
            logger.error(ex) { "Error publishing message for appId=$appId, topicId=$topicId" }
        }
    }
}
