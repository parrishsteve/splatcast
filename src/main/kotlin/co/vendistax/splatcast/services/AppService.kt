package co.vendistax.splatcast.services

import co.vendistax.splatcast.database.tables.Apps
import co.vendistax.splatcast.database.tables.Topics
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.models.App
import co.vendistax.splatcast.models.CreateAppRequest
import co.vendistax.splatcast.models.UpdateAppRequest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

class AppNotFoundException(appName: String = "", appId: Long? = null) :
    NoSuchElementException("App not found: $appName${appId ?: ""}")

class AppNameAlreadyExistsException(name: String) :
    IllegalArgumentException("App name already exists: $name")

class AppHasTopicsException(appId: Long, topicCount: Long) :
    IllegalStateException("Cannot delete app $appId with $topicCount existing topics")

class AppService(
    private val logger: Logger = LoggerFactory.getLogger<AppService>(),
) {
    companion object {
        private const val MAX_NAME_LENGTH = 100
        private val NAME_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }

    fun findAll(): List<App> = transaction {
        Apps.selectAll().map { toApp(it) }
    }

    fun findById(appId: Long): App = transaction {
        Apps.select { Apps.id eq appId }
            .singleOrNull()
            ?.let { toApp(it) }
            ?: throw AppNotFoundException(appId = appId)
    }

    fun findByName(name: String): App = transaction {
        Apps.select { Apps.name eq name }
            .singleOrNull()
            ?.let { toApp(it) }
            ?: throw AppNotFoundException(appName = name)
    }

    fun create(request: CreateAppRequest): App = transaction {
        validateAppName(request.name)

        val existingApp = Apps.select { Apps.name eq request.name }.firstOrNull()
        if (existingApp != null) {
            throw AppNameAlreadyExistsException(request.name)
        }

        val now = OffsetDateTime.now()

        val appId = Apps.insertAndGetId {
            it[name] = request.name
            it[createdAt] = now
            it[updatedAt] = now
        }

        logger.info { "Created app: id=$appId, name=${request.name}" }

        App(
            appId = appId.value,
            name = request.name,
            createdAt = now.toInstant(),
            updatedAt = now.toInstant()
        )
    }

    fun update(appId: Long, request: UpdateAppRequest): App = transaction {
        val existingApp = Apps.select { Apps.id eq appId }.firstOrNull()
            ?: throw AppNotFoundException(appId = appId)

        validateAppName(request.name)

        val duplicateApp = Apps.select {
            (Apps.name eq request.name) and (Apps.id neq appId)
        }.firstOrNull()

        if (duplicateApp != null) {
            throw AppNameAlreadyExistsException(request.name)
        }

        val now = OffsetDateTime.now()

        Apps.update({ Apps.id eq appId }) {
            it[name] = request.name
            it[updatedAt] = now
        }

        logger.info { "Updated app: id=$appId, name=${request.name}" }

        App(
            appId = appId,
            name = request.name,
            createdAt = existingApp[Apps.createdAt].toInstant(),
            updatedAt = now.toInstant()
        )
    }

    fun delete(appId: Long): Unit = transaction {
        val app = Apps.select { Apps.id eq appId }.firstOrNull()
            ?: throw AppNotFoundException(appId = appId)

        val topicCount = Topics.select { Topics.appId eq appId }.count()
        if (topicCount > 0) {
            throw AppHasTopicsException(appId, topicCount)
        }

        Apps.deleteWhere { Apps.id eq appId }

        logger.info { "Deleted app: id=$appId, name=${app[Apps.name]}" }
    }

    private fun validateAppName(name: String) {
        require(name.isNotBlank()) { "App name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) {
            "App name exceeds maximum length of $MAX_NAME_LENGTH"
        }
        require(NAME_PATTERN.matches(name)) {
            "App name can only contain alphanumeric characters, hyphens, and underscores"
        }
    }

    private fun toApp(row: ResultRow): App = App(
        appId = row[Apps.id].value,
        name = row[Apps.name],
        createdAt = row[Apps.createdAt].toInstant(),
        updatedAt = row[Apps.updatedAt].toInstant()
    )
}