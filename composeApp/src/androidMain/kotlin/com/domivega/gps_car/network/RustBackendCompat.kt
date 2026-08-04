package com.domivega.gps_car.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.time.Instant

private val lenientJson = Json { ignoreUnknownKeys = true }

/** RFC3339 / ISO-8601 UTC instant string for Rust `timestamp_start: DateTime<Utc>`. */
fun rfc3339FromEpochMillis(ms: Long): String = Instant.ofEpochMilli(ms).toString()

/**
 * Prefer server-returned track id when present; otherwise use epoch-millis string of the
 * client start instant (Rust `parse_legacy_key` accepts millis and matches the start key).
 */
fun resolveTrackingId(serverBody: String, startedAtMs: Long): String {
    val trimmed = serverBody.trim()
    if (trimmed.isNotEmpty()) {
        runCatching {
            val id = lenientJson.parseToJsonElement(trimmed)
                .jsonObject["id"]
                ?.jsonPrimitive
                ?.contentOrNull
            if (!id.isNullOrBlank()) return id
        }
    }
    return startedAtMs.toString()
}

/** Derive public `/health` URL from any absolute track URL on the same origin. */
fun healthUrlFromTrackUrl(trackUrl: String): String? {
    val raw = trackUrl.trim()
    if (raw.isEmpty()) return null
    return runCatching {
        val uri = URI(raw)
        if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        "${uri.scheme}://${uri.host}$port/health"
    }.getOrNull()
}
