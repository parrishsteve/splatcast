package co.vendistax.splatcast.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TransformerExecutionResult(
    val transformerId: Long,
    val fromSchemaId: Long?,
    val toSchemaId: Long,
    val originalData: JsonObject,
    val transformedData: JsonObject
)

@Serializable
data class TransformerTestResult(
    val transformerId: Long,
    val inputJson: JsonObject,
    val expectedJson: JsonObject,
    val actualJson: JsonObject?,
    val matches: Boolean,
    val executionTimeMs: Long,
    val error: String? = null
)
