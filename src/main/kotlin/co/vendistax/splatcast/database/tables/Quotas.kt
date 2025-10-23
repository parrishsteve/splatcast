package co.vendistax.splatcast.database.tables

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object Quotas : IdTable<String>("quotas") {
    override val id = text("id").entityId()
    val appId = text("app_id").references(Apps.id)
    val topicId = text("topic_id").references(Topics.id)
    val perMinute = integer("per_minute").default(6000)
    val perDay = integer("per_day").default(1000000)
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
