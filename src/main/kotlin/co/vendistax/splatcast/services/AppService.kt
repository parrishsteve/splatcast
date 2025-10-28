package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.tables.Apps
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.App
import co.vendistax.splatcast.models.CreateAppRequest
import co.vendistax.splatcast.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

class AppService(
    private val logger: Logger = LoggerFactory.getLogger<AppService>(),
) {

    fun findAll(): List<App> = transaction {
        Apps.selectAll().map { toApp(it) }
    }

    fun findById(appId: String): App? = transaction {
        Apps.select { Apps.id eq appId }
            .singleOrNull()
            ?.let { toApp(it) }
    }

    fun create(request: CreateAppRequest): App = transaction {
        val appId = "app_${System.currentTimeMillis()}" // Or use UUID/ULID
        val now = OffsetDateTime.now()

        Apps.insert {
            it[Apps.id] = appId
            it[name] = request.name
            it[createdAt] = now
            it[updatedAt] = now
        }

        App(appId, request.name, now.toInstant(), now.toInstant())
    }

    fun update(appId: String, request: UpdateAppRequest): App = transaction {
        Apps.update({ Apps.id eq appId }) {
            it[name] = request.name
            it[updatedAt] = OffsetDateTime.now()
        }
        findById(appId) ?: throw NoSuchElementException("App not found")
    }

    fun delete(appId: String) = transaction {
        Apps.deleteWhere { Apps.id eq appId }
    }

    private fun toApp(row: ResultRow): App = App(
        appId = row[Apps.id].value,
        name = row[Apps.name],
        createdAt = row[Apps.createdAt].toInstant(), // TODO Steve this could be a string, no need to...
        // convert tio Instant just to be immediately converted to a string in the serializer.
        updatedAt = row[Apps.updatedAt].toInstant()
    )
}