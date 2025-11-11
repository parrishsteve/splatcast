package co.vendistax.splatcast.services.facilities

import co.vendistax.splatcast.Config
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.PublishEventResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toDuration

class IdempotencyCache(
    private val cacheExpiration: Duration = Config.IDEMPOTENCY_CACHE_MINUTES.minutes,
    private val cleanupInterval: Duration = Config.IDEMPOTENCY_CACHE_CLEANUP_INTERVAL_MINUTES.minutes,
    private val logger: Logger = LoggerFactory.getLogger<IdempotencyCache>()
) {
    private data class CachedResponse(
        val response: PublishEventResponse,
        val cachedAt: Instant
    )

    private val cache = ConcurrentHashMap<String, CachedResponse>()

    private var cleanupJob: Job? = null

    init {
        cleanupJob = startCleanupTask()
    }

    fun get(key: String): PublishEventResponse? {
        val cached = cache[key] ?: return null
        val age = cached.cachedAt.ageFromNow()

        return if (age < cacheExpiration) {
            logger.debug { "Cache hit for idempotency key: $key" }
            cached.response
        } else {
            logger.debug { "Expired cache entry for idempotency key: $key" }
            cache.remove(key)
            null
        }
    }

    fun put(key: String, response: PublishEventResponse) {
        cache[key] = CachedResponse(
            response = response,
            cachedAt = Instant.now()
        )
        logger.debug { "Cached response for idempotency key: $key" }
    }

    fun close() {
        cleanupJob?.cancel()
    }

    private fun startCleanupTask(): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(cleanupInterval)
                cleanupExpired()
            }
        }
    }

    private fun cleanupExpired() {
        val expired = cache.entries.filter { (_, cached) ->
            cached.cachedAt.ageFromNow() >= cacheExpiration
        }
        logger.info { "Cache Size: ${size()}, removing ${expired.size} entries" }
        expired.forEach { cache.remove(it.key) }

        if (expired.isNotEmpty()) {
            logger.debug { "Cleaned up ${expired.size} expired idempotency cache entries" }
        }
    }

    fun size(): Int = cache.size
}

private fun Instant.ageFromNow(): Duration {
    val millis = Instant.now().toEpochMilli() - this.toEpochMilli()
    return millis.toDuration(kotlin.time.DurationUnit.MILLISECONDS)
}