package co.vendistax.splatcast.database

import org.postgresql.util.PGobject

class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
    init {
        value = enumValue?.toString()
        type = enumTypeName
    }
}