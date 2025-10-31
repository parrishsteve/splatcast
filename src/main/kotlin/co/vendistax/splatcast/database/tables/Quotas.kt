package co.vendistax.splatcast.database.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object Quotas : LongIdTable("quotas") {
    val appId = reference("app_id", Apps)
    val topicId = reference("topic_id", Topics).nullable()
    val perMinute = integer("per_minute")
    val perDay = integer("per_day")

    init {
        uniqueIndex(appId, topicId)
    }
}
