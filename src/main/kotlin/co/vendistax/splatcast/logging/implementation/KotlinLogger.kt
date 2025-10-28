package co.vendistax.splatcast.logging.implementation

import co.vendistax.splatcast.logging.Logger
import mu.KotlinLogging

class KotlinLogger(name: String) : Logger {
    private val logger = KotlinLogging.logger(name)

    override fun trace(message: () -> String): Unit = logger.trace(message)
    override fun trace(message: String, vararg params: Pair<String, Any?>) {
        logger.trace { "$message ${params.joinToString(", ") { "${it.first}=${it.second}" }}" }
    }

    override fun info(message: () -> String): Unit = logger.info(message)
    override fun info(message: String, vararg params: Pair<String, Any?>) {
        logger.info { "$message ${params.joinToString(", ") { "${it.first}=${it.second}" }}" }
    }

    override fun debug(message: () -> String): Unit = logger.debug(message)
    override fun debug(message: String, vararg params: Pair<String, Any?>) {
        logger.debug { "$message ${params.joinToString(", ") { "${it.first}=${it.second}" }}" }
    }

    override fun warn(message: () -> String): Unit = logger.warn(message)
    override fun warn(message: String, vararg params: Pair<String, Any?>) {
        logger.warn { "$message ${params.joinToString(", ") { "${it.first}=${it.second}" }}" }
    }

    override fun error(message: () -> String): Unit = logger.error(message)
    override fun error(message: String, vararg params: Pair<String, Any?>) {
        logger.error { "$message ${params.joinToString(", ") { "${it.first}=${it.second}" }}" }
    }

    override fun error(throwable: Throwable, message: () -> String): Unit = logger.error(throwable, message)
    override fun error(
        throwable: Throwable,
        message: String,
        vararg params: Pair<String, Any?>
    ) {
        logger.error { "$message ${params.joinToString(", ") { "${it.first}=${it.second}" }} errorMsg:${throwable.message} " }
    }
}