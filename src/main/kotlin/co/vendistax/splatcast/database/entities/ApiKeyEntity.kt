package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.ApiKeys
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ApiKeyEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ApiKeyEntity>(ApiKeys)

    var appId by ApiKeys.appId
    var role by ApiKeys.role
    var label by ApiKeys.label
    var keyHash by ApiKeys.keyHash
    var createdAt by ApiKeys.createdAt
    var lastUsedAt by ApiKeys.lastUsedAt
}
