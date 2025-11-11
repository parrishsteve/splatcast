package co.vendistax.splatcast.queue

import java.time.Instant

data class QueueChannel(
    val appId: String,
    val topicId: String,
) {
    override fun toString(): String = "${appId}__${topicId}"
}

interface QueueBusConsumer {
    val channel: QueueChannel
    val fromTimestamp: Long?
    fun start(handler: suspend (queueChannel: QueueChannel, message: String) -> Unit)
    fun stop()
}

interface QueueBusProducer {
    fun send(channel: QueueChannel, message: String)
    fun destroy()
}

