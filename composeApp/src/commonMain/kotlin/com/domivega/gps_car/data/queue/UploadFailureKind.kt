package com.domivega.gps_car.data.queue

enum class UploadFailureKind {
    Transient,
    Permanent,
}

/**
 * Classifies upload/flush error messages so transient network faults do not burn attempts.
 */
object UploadFailureClassifier {
    private val httpCode = Regex("""http\s*(\d{3})""", RegexOption.IGNORE_CASE)

    fun classify(message: String): UploadFailureKind {
        val m = message.lowercase()
        if (m.contains("decode")) return UploadFailureKind.Permanent

        httpCode.find(m)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { code ->
            if (code == 408 || code == 429 || code in 500..599) {
                return UploadFailureKind.Transient
            }
            if (code in 400..499) {
                return UploadFailureKind.Permanent
            }
        }

        val transientHints = listOf(
            "timeout",
            "unable to resolve",
            "failed to connect",
            "connection reset",
            "connection refused",
            "network",
            "unreachable",
            "ssl",
            "broken pipe",
            "stream was reset",
        )
        if (transientHints.any { m.contains(it) }) {
            return UploadFailureKind.Transient
        }

        // Fail-open: unknown IO-style errors should not push rows to DEAD.
        return UploadFailureKind.Transient
    }
}
