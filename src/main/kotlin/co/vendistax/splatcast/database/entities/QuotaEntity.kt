package co.vendistax.splatcast.database.entities

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class QuotaEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, QuotaEntity>(co.vendistax.splatcast.database.tables.Quotas)

    var appId by co.vendistax.splatcast.database.tables.Quotas.appId
    var topicId by co.vendistax.splatcast.database.tables.Quotas.topicId
    var perMinute by co.vendistax.splatcast.database.tables.Quotas.perMinute
    var perDay by co.vendistax.splatcast.database.tables.Quotas.perDay
    var createdAt by co.vendistax.splatcast.database.tables.Quotas.createdAt
}