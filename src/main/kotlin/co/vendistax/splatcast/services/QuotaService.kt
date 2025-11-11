package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.entities.QuotaEntity
import co.vendistax.splatcast.database.tables.Quotas
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class CachedQuotaSettings(
    val perMinute: Int,
    val perDay: Int
)

//Needed since concurrent hashmaps don't allow null, so "no" settings will be represented as 'None'
sealed class CachedQuotaWrapper {
    data class Present(val settings: CachedQuotaSettings) : CachedQuotaWrapper()
    object None : CachedQuotaWrapper()
}

// This is intentionally not "perfect". The quotas are enforced in memory, however it's not a hard enforcement
// The way the concurrent updates are handled means that in high throughput situations, it's possible to exceed
// the quota slightly, but it will self-correct quickly. This is a trade-off to avoid locking and high contention
// on the counters in a distributed system. For most use cases, this level of enforcement is sufficient.

class QuotaService(
    private val logger: Logger = LoggerFactory.getLogger<QuotaService>()
) {
    private data class QuotaCounter(
        var minuteCount: Int = 0,
        var minuteResetTime: Long = 0,
        var dayCount: Int = 0,
        var dayResetTime: Long = 0
    )

    private val counters = ConcurrentHashMap<String, QuotaCounter>()
    private val quotaLimits = ConcurrentHashMap<String, CachedQuotaWrapper>()

    private fun getQuotaSettings(appId: Long, topicId: Long?): CachedQuotaSettings? {
        val key = "$appId:${topicId ?: "null"}"

        return when (val limits = quotaLimits[key]) {
            is CachedQuotaWrapper.Present -> limits.settings
            is CachedQuotaWrapper.None -> null
            null -> {
                // Fetch from database asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    val settings = transaction {
                        QuotaEntity.find { (Quotas.appId eq appId) and (Quotas.topicId eq topicId) }
                            .firstOrNull()
                            ?.let {
                                CachedQuotaSettings(
                                    perMinute = it.perMinute,
                                    perDay = it.perDay
                                )
                            }
                    }
                    quotaLimits[key] = settings?.let { CachedQuotaWrapper.Present(it) } ?: CachedQuotaWrapper.None
                }
                null
            }
        }
    }

    fun checkAndIncrementQuota(appId: Long, topicId: Long): Boolean {
        val key = "$appId:$topicId"
        val now = Instant.now().epochSecond

        val quotaSettings = getQuotaSettings(appId, topicId) ?: return true

        val counter = counters.computeIfAbsent(key) {
            QuotaCounter(
                minuteCount = 0,
                minuteResetTime = now + 60,
                dayCount = 0,
                dayResetTime = now + 86400
            )
        }

        // Synchronize all read-modify-write operations
        synchronized(counter) {
            val currentTime = Instant.now().epochSecond

            if (currentTime > counter.minuteResetTime) {
                counter.minuteCount = 0
                counter.minuteResetTime = currentTime + 60
            }

            if (currentTime > counter.dayResetTime) {
                counter.dayCount = 0
                counter.dayResetTime = currentTime + 86400
            }

            if (counter.minuteCount >= quotaSettings.perMinute ||
                counter.dayCount >= quotaSettings.perDay) {
                return false
            }

            counter.minuteCount++
            counter.dayCount++
            return true
        }
    }

    // Clear cache for a specific app/topic when quota settings change
    fun invalidateCache(appId: Long, topicId: Long) {
        logger.info("Invalidating quota cache for appId=$appId, topicId=$topicId")
        val key = "$appId:$topicId"
        quotaLimits.remove(key)
        counters.remove(key)
    }
}