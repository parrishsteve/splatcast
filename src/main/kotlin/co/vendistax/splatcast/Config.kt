package co.vendistax.splatcast

// Constants used across the Splatcast application
object Config {
    const val BASE_URL = ""
    const val NAME_URL_PREFACE = "by-name"


    const val KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
    const val KAFKA_GROUP_ID = "splatcast-consumer-group"
    const val WEBSOCKET_PING_INTERVAL_MS = 15_000L
    const val WEBSOCKET_TIMEOUT_MS = 30_000L
    const val MAX_WEBSOCKET_FRAME_SIZE = 1_048_576 // 1 MB
}