package co.vendistax.splatcast.queue

interface QueueBus {
    /**
     * Publish a plain string message for an app/topic.
     */
    fun publish(appId: String, topicId: String, message: String)

    /**
     * Subscribe to messages for an app/topic. Handler is called for each incoming message.
     */
    fun subscribe(appId: String, topicId: String, handler: suspend (appId: String, topicId: String, message: String) -> Unit)

    /**
     * Gracefully stop background work and release resources.
     */
    fun close()
}

