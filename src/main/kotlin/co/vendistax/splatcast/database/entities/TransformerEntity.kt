package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Transformers
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TransformerEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<TransformerEntity>(Transformers)

    var appId by Transformers.appId
    var topicId by Transformers.topicId
    var name by Transformers.name
    var fromSchemaId by Transformers.fromSchemaId
    var toSchemaId by Transformers.toSchemaId
    var lang by Transformers.lang
    var code by Transformers.code
    var codeHash by Transformers.codeHash
    var timeoutMs by Transformers.timeoutMs
    var enabled by Transformers.enabled
    var createdBy by Transformers.createdBy
    var createdAt by Transformers.createdAt
}

