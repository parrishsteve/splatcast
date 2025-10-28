package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TransformerExecutionResult(
    val transformId: String,
    val fromSchema: String?,
    val toSchema: String,
    val originalData: JsonObject,
    val transformedData: JsonObject
)

@Serializable
data class TransformerTestResult(
    val transformId: String,
    val inputJson: JsonObject,
    val expectedJson: JsonObject,
    val actualJson: JsonObject?,
    val matches: Boolean,
    val executionTimeMs: Long,
    val error: String? = null
)
