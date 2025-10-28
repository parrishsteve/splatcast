package co.vendistax.splatcast.logging

interface Logger {
    fun trace(message: () -> String)
    fun trace(message: String, vararg params: Pair<String, Any?>)
    fun debug(message: () -> String)
    fun debug(message: String, vararg params: Pair<String, Any?>)
    fun info(message: () -> String)
    fun info(message: String, vararg params: Pair<String, Any?>)
    fun warn(message: () -> String)
    fun warn(message: String, vararg params: Pair<String, Any?>)
    fun error(message: () -> String)
    fun error(message: String, vararg params: Pair<String, Any?>)
    fun error(throwable: Throwable, message: () -> String)
    fun error(throwable: Throwable, message: String, vararg params: Pair<String, Any?>)
}