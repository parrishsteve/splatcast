package co.vendistax.splatcast.queue.implementation

import co.vendistax.splatcast.queue.QueueBus
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KafkaQueueBus(
    bootstrapServers: String = "localhost:29092",
    groupId: String = "splatcast-server",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : QueueBus {

    private val producer = KafkaProducer<String, String>(producerProps(bootstrapServers))

    private val handlers = ConcurrentHashMap<String, MutableList<suspend (String, String, String) -> Unit>>()
    private val subscribedTopics = ConcurrentHashMap.newKeySet<String>()

    private val consumerMutex = Mutex()
    private var consumer: KafkaConsumer<String, String>? = null
    private var pollJob: Job? = null

    private val consumerProps = consumerProps(bootstrapServers, groupId)

    private fun key(appId: String, topicId: String) = "$appId.$topicId"

    override fun publish(appId: String, topicId: String, message: String) {
        val topic = key(appId, topicId)
        producer.send(ProducerRecord(topic, message))
    }

    override fun subscribe(appId: String, topicId: String, handler: suspend (String, String, String) -> Unit) {
        val topic = key(appId, topicId)

        handlers.compute(topic) { _, list ->
            (list ?: mutableListOf()).apply { add(handler) }
        }

        val isNewTopic = subscribedTopics.add(topic)

        if (isNewTopic) {
            scope.launch {
                consumerMutex.withLock {
                    // Create consumer and start polling if this is the first subscription
                    if (consumer == null) {
                        consumer = KafkaConsumer<String, String>(consumerProps)
                        startPolling()
                    }

                    // Update subscription with all topics
                    consumer?.subscribe(subscribedTopics.toSet())
                }
            }
        }
    }

    private fun startPolling() {
        pollJob = scope.launch {
            val kafkaConsumer = consumer ?: return@launch

            try {
                while (isActive) {
                    val records = try {
                        kafkaConsumer.poll(Duration.ofMillis(500))
                    } catch (ex: org.apache.kafka.common.errors.WakeupException) {
                        break // exit gracefully on wakeup
                    }

                    for (record in records) {
                        val topic = record.topic()
                        val (appId, topicId) = parseTopic(topic)
                        val msg = record.value()

                        handlers[topic]?.forEach { handler ->
                            launch { handler(appId, topicId, msg) }
                        }
                    }
                }
            } finally {
                try { kafkaConsumer.close() } catch (_: Throwable) {}
            }
        }
    }

    override fun close() {
        runBlocking {
            consumer?.wakeup() // interrupt any ongoing poll
            pollJob?.cancelAndJoin() // wait for poll loop to finish

            try {
                producer.flush()
                producer.close()
            } catch (_: Throwable) {}

            scope.cancel()
        }
    }

    private fun parseTopic(topic: String): Pair<String, String> {
        val parts = topic.split('.', limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else "unknown" to topic
    }

    private fun producerProps(bootstrap: String): Properties = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
    }

    private fun consumerProps(bootstrap: String, group: String): Properties = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.GROUP_ID_CONFIG, group)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    }
}
