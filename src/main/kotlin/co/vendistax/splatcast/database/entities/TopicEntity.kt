package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Topics
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TopicEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, TopicEntity>(Topics)

    var appId by Topics.appId
    var name by Topics.name
    var description by Topics.description
    var retentionHours by Topics.retentionHours
    var defaultSchemaId by Topics.defaultSchemaId
    var createdAt by Topics.createdAt
    var updatedAt by Topics.updatedAt
}