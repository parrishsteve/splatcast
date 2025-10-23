package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Schemas
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class SchemaEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, SchemaEntity>(Schemas)

    var appId by Schemas.appId
    var topicId by Schemas.topicId
    var version by Schemas.version
    private var _rawJsonSchema by Schemas.jsonSchema
    var jsonSchema: JsonObject
        get() = Json.parseToJsonElement(_rawJsonSchema.orEmpty()).jsonObject
        set(value) { _rawJsonSchema = value.toString() }
    var status by Schemas.status
    var createdAt by Schemas.createdAt

    // Reference to topic entity
    val topic by TopicEntity referencedOn Schemas.topicId
}
