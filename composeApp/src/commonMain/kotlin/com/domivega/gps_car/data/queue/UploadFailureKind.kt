package com.domivega.gps_car.data.queue

enum class UploadFailureKind {
    Transient,
    Permanent,

    /**
     * 401/403 from an ingest call: the device token is missing, unknown or revoked
     * (phone unlinked from the car on the server). Not the samples' fault, so rows
     * must not burn attempts; uploading pauses until a new token is saved.
     */
    DeviceUnauthorized,
}

/** Why uploading is paused. Persisted; cleared by a new token or a Test connection OK. */
enum class UploadPauseReason {
    DeviceUnauthorized,
    ;

    companion object {
        fun fromName(name: String?): UploadPauseReason? =
            entries.firstOrNull { it.name == name }

        /** A stored name this build no longer knows (a reason removed in an update). */
        fun isObsolete(name: String?): Boolean = name != null && fromName(name) == null
    }
}

/** Pause reason for a failure kind, or null when the failure does not pause uploads. */
fun UploadFailureKind.pauseReason(): UploadPauseReason? = when (this) {
    UploadFailureKind.DeviceUnauthorized -> UploadPauseReason.DeviceUnauthorized
    UploadFailureKind.Transient, UploadFailureKind.Permanent -> null
}

/**
 * Classifies upload/flush error messages so transient network faults do not burn attempts.
 */
object UploadFailureClassifier {
    private val httpCode = Regex("""http\s*(\d{3})""", RegexOption.IGNORE_CASE)

    /** Classifies an HTTP error status. */
    fun classifyHttp(code: Int): UploadFailureKind {
        if (code == 401 || code == 403) return UploadFailureKind.DeviceUnauthorized
        if (code == 408 || code == 429 || code in 500..599) return UploadFailureKind.Transient
        if (code in 400..499) return UploadFailureKind.Permanent
        // Anything else is not a client error; do not DEAD rows over it.
        return UploadFailureKind.Transient
    }

    /** Classifies an error message such as `HTTP 401: <body>` or an IO exception message. */
    fun classify(message: String): UploadFailureKind {
        val m = message.lowercase()

        // Auth first: a rejected token is never the payload's fault, even if
        // the body happens to mention decoding.
        val code = httpCode.find(m)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (code != null && classifyHttp(code) == UploadFailureKind.DeviceUnauthorized) {
            return UploadFailureKind.DeviceUnauthorized
        }

        if (m.contains("decode")) return UploadFailureKind.Permanent

        if (code != null) {
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
