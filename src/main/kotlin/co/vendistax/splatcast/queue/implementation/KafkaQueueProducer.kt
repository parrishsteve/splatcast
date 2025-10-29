package co.vendistax.splatcast.queue.implementation

import co.vendistax.splatcast.queue.QueueBusProducer
import co.vendistax.splatcast.queue.QueueChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class KafkaQueueProducer (
    bootstrapServers: String = "localhost:29092",
): QueueBusProducer  {

    private val producer = KafkaProducer<String, String>(producerProps(bootstrapServers))

    override fun send(channel: QueueChannel, message: String) {
        producer.send(ProducerRecord(channel.toString(), message))
    }

    suspend fun publishAsyncAndAwait(appId: String, topicId: String, message: String) {
        withContext(Dispatchers.IO) {
            val future = producer.send(ProducerRecord("${appId}__${topicId}", message))
            future.get()
        }
    }

    override fun destroy() {
        try {
            producer.flush()
            producer.close()
        } catch (_: Throwable) {}
    }

    private fun producerProps(bootstrap: String): Properties = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.ACKS_CONFIG, "all")
    }
}
