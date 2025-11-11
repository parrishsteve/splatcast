package co.vendistax.splatcast.queue.implementation

import co.vendistax.splatcast.Config
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.queue.QueueBusConsumer
import co.vendistax.splatcast.queue.QueueChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

class KafkaQueueConsumer(
    bootstrapServers: String = Config.KAFKA_BOOTSTRAP_SERVERS,
    groupId: String = Config.KAFKA_GROUP_ID,
    override val channel: QueueChannel,
    override val fromTimestamp: Long?,
    private val logger: Logger = LoggerFactory.getLogger<KafkaQueueConsumer>()
) : QueueBusConsumer {

    // Use SupervisorJob at scope level for proper supervision
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val consumerProps = consumerProps(bootstrapServers, groupId)
    private val consumerMutex = Mutex()
    private var consumer: KafkaConsumer<String, String>? = null
    private var pollJob: Job? = null

    private val handlerRef = AtomicReference<suspend (QueueChannel, String) -> Unit?>(null)

    // Track if consumer is running
    @Volatile
    private var isRunning = false

    override fun start(handler: suspend (QueueChannel, String) -> Unit) {
        // Allow restart with new handler after stop
        val previousHandler = handlerRef.getAndSet(handler)
        if (previousHandler != null && isRunning) {
            handlerRef.set(previousHandler) // Restore previous handler
            throw IllegalStateException("Consumer already running. Call stop() first to change handler")
        }

        isRunning = true

        scope.launch {
            consumerMutex.withLock {
                val topic = channel.toString()
                if (consumer == null) {
                    consumer = KafkaConsumer<String, String>(consumerProps).also {
                        //it.subscribe(listOf(topic))
                    }
                    val localConsumer = consumer // TODO Do we need a flow to emit errors?

                    val partitionInfos = localConsumer?.partitionsFor(topic)
                    val partitions = partitionInfos?.map {
                        org.apache.kafka.common.TopicPartition(topic, it.partition())
                    }

                    // Manually assign partitions instead of subscribing
                    localConsumer?.assign(partitions)

                    // if we have a timestamp to start from, then poll once and seek to that timestamp
                    if (fromTimestamp != null && localConsumer != null) {
                        //localConsumer.poll(Duration.ofSeconds(10)) // this is need to get the assignments
                        //val partitions = localConsumer.assignment()
                        if (partitions?.isNotEmpty() == true) {
                            val timestampsToSearch = partitions.associateWith { fromTimestamp }
                            val offsetsForTimes = localConsumer.offsetsForTimes(timestampsToSearch)
                            for ((partition, offsetAndTimestamp) in offsetsForTimes) {
                                if (offsetAndTimestamp != null) {
                                    localConsumer.seek(partition, offsetAndTimestamp.offset())
                                    logger.debug {
                                        "Seeking partition ${partition.partition()} to " +
                                                "offset ${offsetAndTimestamp.offset()} for timestamp ${fromTimestamp.toString()}"
                                    }
                                } else {
                                    // No offset found for timestamp, seek to beginning
                                    localConsumer.seekToBeginning(listOf(partition))
                                    logger.debug {
                                        "No offset found for partition ${partition.partition()} " +
                                                "at timestamp ${fromTimestamp.toString()}, seeking to beginning"
                                    }
                                }
                            }
                        }
                    }
                    startPolling()
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
                        logger.debug("Consumer wakeup received for channel: $channel")
                        break
                    }

                    for (record in records) {
                        val msg = record.value()
                        val currentHandler = handlerRef.get() ?: continue

                        // Launch directly on scope (which has SupervisorJob)
                        scope.launch {
                            try {
                                currentHandler(channel, msg)
                            } catch (c: CancellationException) {
                                throw c // Always rethrow cancellation
                            } catch (e: Exception) {
                                logger.error(e) { "Handler error for channel $channel: ${e.message}" }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) {"Polling error for channel $channel: ${e.message}" }
            } finally {
                withContext(NonCancellable) {
                    try {
                        kafkaConsumer.close()
                        logger.debug("Kafka consumer closed for channel: $channel")
                    } catch (e: Exception) {
                        logger.error(e) { "Error closing consumer: ${e.message}" }
                    }
                }
            }
        }
    }

    override fun stop() {
        isRunning = false
        handlerRef.set(null)

        // Use async to avoid blocking
        scope.launch {
            consumerMutex.withLock {
                consumer?.wakeup()
                pollJob?.cancelAndJoin()
                consumer = null
                pollJob = null
            }
        }.invokeOnCompletion {
            // Cancel scope after cleanup
            scope.cancel()
        }
    }

    /**
     * Suspending version of stop for coroutine contexts
     */
    suspend fun stopAsync() {
        isRunning = false
        handlerRef.set(null)

        consumerMutex.withLock {
            consumer?.wakeup()
            pollJob?.cancelAndJoin()
            consumer = null
            pollJob = null
        }

        scope.coroutineContext.cancelChildren()
        scope.cancel()
    }

    private fun consumerProps(bootstrap: String, group: String): Properties = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.GROUP_ID_CONFIG, group)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100)
        put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
    }
}
