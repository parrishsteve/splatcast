package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Apps
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class AppEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, AppEntity>(Apps)

    var name by Apps.name
    var createdAt by Apps.createdAt
    var updatedAt by Apps.updatedAt
}