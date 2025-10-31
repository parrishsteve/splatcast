package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Topics
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TopicEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<TopicEntity>(Topics)

    var appId by Topics.appId
    var name by Topics.name
    var description by Topics.description
    var retentionHours by Topics.retentionHours
    var defaultSchemaId by Topics.defaultSchemaId
    var createdAt by Topics.createdAt
    var updatedAt by Topics.updatedAt
}
