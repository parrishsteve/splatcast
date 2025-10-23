package co.vendistax.splatcast.validation

class ValidationException(message: String) : IllegalArgumentException(message)

fun String?.validateRequired(fieldName: String): String {
    if (this.isNullOrBlank()) {
        throw ValidationException("$fieldName is required")
    }
    return this
}

fun String.validateLength(fieldName: String, min: Int? = null, max: Int? = null): String {
    min?.let {
        if (this.length < it) {
            throw ValidationException("$fieldName must be at least $it characters")
        }
    }
    max?.let {
        if (this.length > it) {
            throw ValidationException("$fieldName must be at most $it characters")
        }
    }
    return this
}

fun String.validatePattern(fieldName: String, pattern: Regex, message: String? = null): String {
    if (!this.matches(pattern)) {
        throw ValidationException(message ?: "$fieldName format is invalid")
    }
    return this
}

fun String.validateEmail(fieldName: String): String {
    val emailPattern = Regex("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")
    return this.validatePattern(fieldName, emailPattern, "$fieldName must be a valid email address")
}

fun String.validateUrl(fieldName: String): String {
    val urlPattern = Regex("^https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+$")
    return this.validatePattern(fieldName, urlPattern, "$fieldName must be a valid URL")
}

fun Int.validateRange(fieldName: String, min: Int? = null, max: Int? = null): Int {
    min?.let {
        if (this < it) {
            throw ValidationException("$fieldName must be at least $it")
        }
    }
    max?.let {
        if (this > it) {
            throw ValidationException("$fieldName must be at most $it")
        }
    }
    return this
}

fun <T> Collection<T>.validateSize(fieldName: String, min: Int? = null, max: Int? = null): Collection<T> {
    min?.let {
        if (this.size < it) {
            throw ValidationException("$fieldName must contain at least $it items")
        }
    }
    max?.let {
        if (this.size > it) {
            throw ValidationException("$fieldName must contain at most $it items")
        }
    }
    return this
}
