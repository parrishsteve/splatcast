package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Schemas
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class SchemaEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SchemaEntity>(Schemas)

    var appId by Schemas.appId
    var name by Schemas.name
    var jsonSchema by Schemas.jsonSchema
    var status by Schemas.status
    var createdAt by Schemas.createdAt
}

