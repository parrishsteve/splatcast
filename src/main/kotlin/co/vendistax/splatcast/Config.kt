package co.vendistax.splatcast

// Constants used across the Splatcast application
object Config {
    const val BASE_URL = ""
    const val NAME_URL_PREFACE = "by-name"

    // Idempotency cache settings
    const val IDEMPOTENCY_CACHE_MINUTES = 480 // 8 hours
    const val IDEMPOTENCY_CACHE_CLEANUP_INTERVAL_MINUTES = 60 // 1 hour

    const val KAFKA_BOOTSTRAP_SERVERS = "localhost:29092"
    const val KAFKA_GROUP_ID = "splatcast-server"
    const val WEBSOCKET_PING_INTERVAL_SECS = 15L
    const val WEBSOCKET_TIMEOUT_SECS = 30L
    const val MAX_WEBSOCKET_FRAME_SIZE = Long.MAX_VALUE // 1_048_576L // 1 MB
}