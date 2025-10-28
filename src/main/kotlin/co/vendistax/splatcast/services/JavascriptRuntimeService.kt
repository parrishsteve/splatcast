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

class JavaScriptRuntimeService(
    private val logger: Logger = LoggerFactory.getLogger<JavaScriptRuntimeService>(),
) {

    private val engine = Engine.newBuilder("js")
        .option("engine.WarnInterpreterOnly", "false")
        .build()

    fun executeTransform(
        jsCode: String,
        inputData: JsonObject,
        timeoutMs: Int = 50
    ): Result<JsonObject> {
        return try {
            val context = createSecureContext(timeoutMs)

            context.use { ctx ->
                // Convert JsonObject to JSON string for JavaScript
                val inputJson = Json.encodeToString(inputData)

                // Create the transform function wrapper
                val wrappedCode = """
                    const transform = $jsCode;
                    const input = JSON.parse('${inputJson.replace("'", "\\'")}');
                    const result = transform(input);
                    JSON.stringify(result);
                """.trimIndent()

                // Execute the JavaScript code
                val jsResult = ctx.eval("js", wrappedCode)

                if (jsResult.isString) {
                    val resultJson = jsResult.asString()
                    val transformedData = Json.parseToJsonElement(resultJson).jsonObject
                    Result.success(transformedData)
                } else {
                    Result.failure(Exception("Transform did not return a valid JSON object"))
                }
            }
        } catch (e: Exception) {
            when {
                e.message?.contains("timeout") == true ->
                    Result.failure(Exception("Transform execution timed out after ${timeoutMs}ms"))
                e.message?.contains("polyglot") == true ->
                    Result.failure(Exception("JavaScript execution error: ${e.message}"))
                else ->
                    Result.failure(Exception("Transform failed: ${e.message}"))
            }
        }
    }

    fun validateTransformSyntax(jsCode: String): Result<Unit> {
        return try {
            val context = createSecureContext(1000) // 1 second for validation

            context.use { ctx ->
                // Just parse the function, don't execute
                val testCode = """
                    const transform = $jsCode;
                    if (typeof transform !== 'function') {
                        throw new Error('Code must export a function');
                    }
                """.trimIndent()

                ctx.eval("js", testCode)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Invalid JavaScript syntax: ${e.message}"))
        }
    }

    private fun createSecureContext(timeoutMs: Int): Context {
        return Context.newBuilder("js")
            .engine(engine)
            .allowHostAccess(HostAccess.NONE) // Disable access to host classes
            .allowIO(false) // Disable file system access
            .allowNativeAccess(false) // Disable native code access
            .allowCreateThread(false) // Disable thread creation
            .allowEnvironmentAccess(EnvironmentAccess.NONE) // Disable environment variables
            .resourceLimits(
                ResourceLimits.newBuilder()
                    .statementLimit(10000, null) // Max 10k statements
                    .build()
            )
            .option("js.ecmascript-version", "2022")
            .option("js.strict", "true")
            .option("js.timer-resolution", "1") // 1ms timer resolution
            .build()
            .also { ctx ->
                // Set execution timeout
                ctx.initialize("js")
                Thread {
                    Thread.sleep(timeoutMs.toLong())
                    try {
                        ctx.close(true) // Force close on timeout
                    } catch (e: Exception) {
                        // Context might already be closed
                    }
                }.start()
            }
    }
}
