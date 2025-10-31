package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Quotas
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class QuotaEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<QuotaEntity>(Quotas)

    var appId by Quotas.appId
    var topicId by Quotas.topicId
    var perMinute by Quotas.perMinute
    var perDay by Quotas.perDay
}