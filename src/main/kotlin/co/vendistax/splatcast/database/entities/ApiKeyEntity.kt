package co.vendistax.splatcast.database.entities

import co.vendistax.splatcast.database.tables.ApiKeys
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ApiKeyEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, ApiKeyEntity>(ApiKeys)

    var appId by ApiKeys.appId
    var role by ApiKeys.role
    var label by ApiKeys.label
    var keyHash by ApiKeys.keyHash
    var createdAt by ApiKeys.createdAt
    var lastUsedAt by ApiKeys.lastUsedAt
}