package co.vendistax.splatcast.database.tables

import co.vendistax.splatcast.database.PGEnum
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

enum class TransformLang {
    JS;

    override fun toString(): String = name.uppercase()

    companion object {
        fun fromString(value: String): TransformLang = valueOf(value.uppercase())
    }
}

object Transformers : LongIdTable("transformers") {
    val appId = reference("app_id", Apps)
    val topicId = reference("topic_id", Topics)
    val name = text("name")
    val fromSchemaId = reference("from_schema", Schemas).nullable()
    val toSchemaId = reference("to_schema", Schemas)
    val lang = customEnumeration("lang", "transform_lang",
        { value -> TransformLang.fromString(value as String) },
        { PGEnum("transform_lang", it) }
    ).default(TransformLang.JS)
    val code = text("code")
    val codeHash = varchar("code_hash", 255).uniqueIndex()
    val timeoutMs = integer("timeout_ms").default(50)
    val enabled = bool("enabled").default(true)
    val createdBy = varchar("created_by", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
}


