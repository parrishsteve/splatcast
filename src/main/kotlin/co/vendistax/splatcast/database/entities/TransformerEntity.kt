package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.Transformers
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TransformerEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, TransformerEntity>(Transformers)

    var appId by Transformers.appId
    var topicId by Transformers.topicId
    var fromSchema by Transformers.fromSchema
    var toSchema by Transformers.toSchema
    var lang by Transformers.lang
    var code by Transformers.code
    var codeHash by Transformers.codeHash
    var timeoutMs by Transformers.timeoutMs
    var enabled by Transformers.enabled
    var createdBy by Transformers.createdBy
    var createdAt by Transformers.createdAt
}
