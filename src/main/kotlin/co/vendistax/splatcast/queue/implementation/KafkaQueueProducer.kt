package co.vendistax.splatcast.queue.implementation

import co.vendistax.splatcast.Config
import co.vendistax.splatcast.queue.QueueBusProducer
import co.vendistax.splatcast.queue.QueueChannel
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class KafkaQueueProducer (
    bootstrapServers: String = Config.KAFKA_BOOTSTRAP_SERVERS,
): QueueBusProducer  {

    private val producer = KafkaProducer<String, String>(producerProps(bootstrapServers))

    override fun send(channel: QueueChannel, message: String) {
        producer.send(ProducerRecord(channel.toString(), message))
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
