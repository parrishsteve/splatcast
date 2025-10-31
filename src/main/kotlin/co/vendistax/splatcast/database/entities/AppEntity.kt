package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Apps
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class AppEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AppEntity>(Apps)

    var name by Apps.name
    var createdAt by Apps.createdAt
    var updatedAt by Apps.updatedAt
}
