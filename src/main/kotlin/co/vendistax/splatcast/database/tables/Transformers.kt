package co.vendistax.splatcast.database.tables

import co.vendistax.splatcast.database.PGEnum
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

enum class TransformLang {
    JS;

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): TransformLang = valueOf(value.uppercase())
    }
}

object Transformers : IdTable<String>("js_transforms") {
    val appId = varchar("app_id", 255).references(Apps.id)
    val topicId = varchar("topic_id", 255).references(Topics.id)
    val fromSchema = varchar("from_schema", 255).nullable()
    val toSchema = varchar("to_schema", 255)

    //val lang = enumeration<TransformLang>("lang").default(TransformLang.JS)
    val lang = customEnumeration("lang", "transform_lang",
        { value -> TransformLang.fromString(value as String) },
        { PGEnum("transform_lang", it) }
    ).default(TransformLang.JS)

    val code = text("code")
    val codeHash = varchar("code_hash", 255)
    val timeoutMs = integer("timeout_ms").default(50)
    val enabled = bool("enabled").default(true)
    val createdBy = varchar("created_by", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault{ OffsetDateTime.now() }

    override val id = varchar("id", 255).entityId()
}

