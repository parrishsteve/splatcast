package co.vendistax.splatcast.plugins

import co.vendistax.splatcast.database.DatabaseFactory
import io.ktor.server.application.Application
import org.flywaydb.core.Flyway

fun Application.configureDatabase() {
    // Run database migrations
    val flyway = Flyway.configure()
        .dataSource(
            System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/splatcast",
            System.getenv("DB_USER") ?: "postgres",
            System.getenv("DB_PASSWORD") ?: "vendi7"
        )
        .locations("classpath:db/migration")
        .load()
    flyway.migrate()

    // Initialize database connection
    DatabaseFactory.init()
}