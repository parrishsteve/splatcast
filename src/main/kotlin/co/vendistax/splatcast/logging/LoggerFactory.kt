package co.vendistax.splatcast.logging

import co.vendistax.splatcast.logging.implementation.KotlinLogger


object LoggerFactory {
    fun getLogger(name: String): Logger = KotlinLogger(name)
    inline fun <reified T> getLogger(): Logger = getLogger(T::class.java.name)
}