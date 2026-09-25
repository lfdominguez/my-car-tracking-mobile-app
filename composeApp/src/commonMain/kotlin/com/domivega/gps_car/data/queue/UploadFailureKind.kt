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

    /**
     * 409 on `/samples` because the car's owner enabled the end-to-end vault, which
     * only accepts encrypted chunk uploads this app does not implement.
     */
    VaultRequired,
}

/** Why uploading is paused. Persisted; cleared by a new token or a Test connection OK. */
enum class UploadPauseReason {
    DeviceUnauthorized,
    VaultRequired,
    ;

    companion object {
        fun fromName(name: String?): UploadPauseReason? =
            entries.firstOrNull { it.name == name }
    }
}

/** Pause reason for a failure kind, or null when the failure does not pause uploads. */
fun UploadFailureKind.pauseReason(): UploadPauseReason? = when (this) {
    UploadFailureKind.DeviceUnauthorized -> UploadPauseReason.DeviceUnauthorized
    UploadFailureKind.VaultRequired -> UploadPauseReason.VaultRequired
    UploadFailureKind.Transient, UploadFailureKind.Permanent -> null
}

/**
 * Classifies upload/flush error messages so transient network faults do not burn attempts.
 */
object UploadFailureClassifier {
    private val httpCode = Regex("""http\s*(\d{3})""", RegexOption.IGNORE_CASE)

    /**
     * Classifies an HTTP error status. [body] only matters for 409, where the vault
     * rejection ("vault car requires encrypted chunk upload") is told apart from
     * other conflicts.
     */
    fun classifyHttp(code: Int, body: String = ""): UploadFailureKind {
        if (code == 401 || code == 403) return UploadFailureKind.DeviceUnauthorized
        if (code == 409 && body.contains("vault", ignoreCase = true)) {
            return UploadFailureKind.VaultRequired
        }
        if (code == 408 || code == 429 || code in 500..599) return UploadFailureKind.Transient
        if (code in 400..499) return UploadFailureKind.Permanent
        // Anything else is not a client error; do not DEAD rows over it.
        return UploadFailureKind.Transient
    }

    /** Classifies an error message such as `HTTP 401: <body>` or an IO exception message. */
    fun classify(message: String): UploadFailureKind {
        val m = message.lowercase()

        // Auth / vault first: a rejected token is never the payload's fault, even if
        // the body happens to mention decoding.
        val match = httpCode.find(m)
        val code = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (match != null && code != null) {
            val body = m.substring(match.range.last + 1)
            val kind = classifyHttp(code, body)
            if (kind == UploadFailureKind.DeviceUnauthorized || kind == UploadFailureKind.VaultRequired) {
                return kind
            }
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
