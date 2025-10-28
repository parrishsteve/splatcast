package co.vendistax.splatcast.plugins


import co.vendistax.splatcast.queue.implementation.KafkaQueueBus
import co.vendistax.splatcast.websocket.TopicWebSocketHub
import io.ktor.server.application.Application

fun Application.configureQueueBus(
    bootstrapServers: String = "localhost:9092",
    webSocketPublisher: TopicWebSocketHub) {
    val queueBus = KafkaQueueBus(bootstrapServers = bootstrapServers)

    // Example: subscribe a specific app/topic and forward all consumed messages to websocket subscribers
    // Replace these ids or wire dynamic subscriptions as needed by your application logic
    //queueBus.subscribe("app_1761081168689", "topic_09779bbc2690") { appId, topicId, message ->
    //    webSocketPublisher.publish(appId, topicId, message)
    //}

    // Example usage for publishing:
    // queueBus.publish("app_1761081168689", "topic_09779bbc2690", """{"foo":"bar"}""")

    // Consider storing queueBus in Application.attributes or a DI container to access elsewhere
}
