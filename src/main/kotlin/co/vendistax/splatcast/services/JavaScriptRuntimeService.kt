package co.vendistax.splatcast.services

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Engine
import org.graalvm.polyglot.EnvironmentAccess
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.ResourceLimits
import org.graalvm.polyglot.PolyglotException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class JavaScriptRuntimeService(
    private val logger: Logger = LoggerFactory.getLogger<JavaScriptRuntimeService>(),
) {
    private val engine = Engine.newBuilder("js")
        .option("engine.WarnInterpreterOnly", "false")
        .build()

    // Create a single shared resource limits instance
    private val resourceLimits = ResourceLimits.newBuilder()
        .statementLimit(JavaScriptRuntimeService.Companion.MAX_STATEMENT_LIMIT) { limits ->
            logger.warn { "Transform exceeded statement limit of ${JavaScriptRuntimeService.Companion.MAX_STATEMENT_LIMIT}" }
            false
        }
        .build()

    private val executorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "js-runtime-${threadCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    companion object {
        private const val MAX_STATEMENT_LIMIT = 100_000L
        private const val MAX_OUTPUT_SIZE_BYTES = 1_048_576 // 1MB
        private val threadCounter = AtomicInteger(0)
    }

    fun executeTransform(
        jsCode: String,
        inputData: JsonObject,
        timeoutMs: Int = 50
    ): Result<JsonObject> {
        val startTime = System.currentTimeMillis()

        return try {
            val future = executorService.submit<Result<JsonObject>> {
                executeInContext(jsCode, inputData, timeoutMs)
            }

            try {
                val result = future.get(timeoutMs + 100L, TimeUnit.MILLISECONDS)

                val executionTime = System.currentTimeMillis() - startTime
                logger.debug { "Transform executed in ${executionTime}ms" }

                result
            } catch (e: TimeoutException) {
                future.cancel(true)
                logger.warn { "Transform timed out after ${timeoutMs}ms" }
                Result.failure(Exception("Transform execution timed out after ${timeoutMs}ms"))
            }
        } catch (e: Exception) {
            logger.error(e, "Transform execution failed")
            Result.failure(Exception("Transform failed: ${sanitizeErrorMessage(e)}"))
        }
    }

    private fun executeInContext(
        jsCode: String,
        inputData: JsonObject,
        timeoutMs: Int
    ): Result<JsonObject> {
        val context = createSecureContext(timeoutMs)

        return try {
            context.use { ctx ->
                // Convert JsonObject to JSON string
                val inputJson = Json.encodeToString(inputData)

                // Use a safer method to pass JSON data - via bindings instead of string interpolation
                ctx.getBindings("js").putMember("__input", inputJson)

                val wrappedCode = """
                    const transform = $jsCode;
                    if (typeof transform !== 'function') {
                        throw new Error('Transform must be a function');
                    }
                    const input = JSON.parse(__input);
                    const result = transform(input);
                    if (typeof result !== 'object' || result === null) {
                        throw new Error('Transform must return an object');
                    }
                    JSON.stringify(result);
                """.trimIndent()

                val jsResult = ctx.eval("js", wrappedCode)

                if (!jsResult.isString) {
                    return Result.failure(Exception("Transform did not return a valid result"))
                }

                val resultJson = jsResult.asString()

                // Validate output size
                if (resultJson.length > MAX_OUTPUT_SIZE_BYTES) {
                    return Result.failure(
                        Exception("Transform output exceeds maximum size of $MAX_OUTPUT_SIZE_BYTES bytes")
                    )
                }

                val transformedData = Json.parseToJsonElement(resultJson).jsonObject
                Result.success(transformedData)
            }
        } catch (e: PolyglotException) {
            when {
                e.isCancelled -> Result.failure(Exception("Transform execution was cancelled"))
                e.isResourceExhausted -> Result.failure(Exception("Transform exceeded resource limits"))
                e.isGuestException -> Result.failure(Exception("Transform error: ${e.message}"))
                else -> Result.failure(Exception("JavaScript runtime error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Transform failed: ${sanitizeErrorMessage(e)}"))
        }
    }

    fun validateTransformSyntax(jsCode: String): Result<Unit> {
        return try {
            val context = createSecureContext(1000)

            context.use { ctx ->
                val testCode = """
                    const transform = $jsCode;
                    if (typeof transform !== 'function') {
                        throw new Error('Transform must be a function');
                    }
                    // Test with empty object
                    const testResult = transform({});
                    if (typeof testResult !== 'object' || testResult === null) {
                        throw new Error('Transform must return an object');
                    }
                """.trimIndent()

                ctx.eval("js", testCode)
                Result.success(Unit)
            }
        } catch (e: PolyglotException) {
            val errorMsg = when {
                e.isGuestException -> "Invalid transform: ${e.message}"
                e.isSyntaxError -> "Syntax error: ${e.message}"
                else -> "Validation error: ${e.message}"
            }
            Result.failure(Exception(errorMsg))
        } catch (e: Exception) {
            Result.failure(Exception("Validation failed: ${sanitizeErrorMessage(e)}"))
        }
    }

    private fun createSecureContext(timeoutMs: Int): Context {
        return Context.newBuilder("js")
            .engine(engine)
            .allowHostAccess(HostAccess.NONE)
            .allowIO(false)
            .allowNativeAccess(false)
            .allowCreateThread(false)
            .allowEnvironmentAccess(EnvironmentAccess.NONE)
            .allowPolyglotAccess(org.graalvm.polyglot.PolyglotAccess.NONE)
            .resourceLimits(resourceLimits)  // Use the shared instance
            .option("js.strict", "true")
            .build()
    }

    private fun sanitizeErrorMessage(e: Exception): String {
        // Remove potentially sensitive stack traces and paths
        return e.message?.take(200) ?: "Unknown error"
    }

    fun shutdown() {
        try {
            executorService.shutdown()
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
            engine.close()
        } catch (e: Exception) {
            logger.error(e, "Error shutting down JavaScript runtime")
        }
    }
}
