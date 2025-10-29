package co.vendistax.splatcast.queue

data class QueueChannel(
    val appId: String,
    val topicId: String,
) {
    override fun toString(): String = "${appId}__${topicId}"
}

interface QueueBusConsumer {
    val channel: QueueChannel
    fun start(handler: suspend (queueChannel: QueueChannel, message: String) -> Unit)
    fun stop()
}

interface QueueBusProducer {
    fun send(channel: QueueChannel, message: String)
    fun destroy()
}

