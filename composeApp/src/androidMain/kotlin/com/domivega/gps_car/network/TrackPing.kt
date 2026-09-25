package com.domivega.gps_car.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `GET /api/track/ping`: authenticated exactly like ingest, but read-only, so
 * Test connection can verify the device token without opening a trip.
 */
object TrackPing {
    /** Path appended to the start URL's origin when no explicit ping URL is set. */
    const val PATH = "/api/track/ping"

    private val json = Json { ignoreUnknownKeys = true }

    /** Explicit [pingUrl] wins; blank derives it from [startUrl]'s origin. */
    fun resolveUrl(pingUrl: String, startUrl: String): String? {
        val explicit = pingUrl.trim()
        if (explicit.isNotEmpty()) return explicit
        return pingUrlFromTrackUrl(startUrl)
    }

    fun parseBody(body: String): PingResponse? =
        runCatching { json.decodeFromString(PingResponse.serializer(), body.trim()) }.getOrNull()

    /**
     * Maps the ping reply to a Test connection result.
     *
     * 401 (bad/missing header) and 403 (unknown or revoked token) both mean this
     * phone's token is not accepted. 404 is an older server without the endpoint:
     * reachable, but the token could not be checked without writing a trip.
     */
    fun classify(code: Int, body: String): ConnectionTestResult = when {
        code in 200..299 -> {
            val parsed = parseBody(body)
            if (parsed == null || !parsed.ok) {
                ConnectionTestResult.Failed("Unexpected ping response (HTTP $code)")
            } else {
                ConnectionTestResult.Ok(
                    carName = parsed.carName?.trim()?.takeIf { it.isNotEmpty() },
                    vaultRequired = parsed.vaultRequired,
                )
            }
        }
        code == 401 || code == 403 -> ConnectionTestResult.Unauthorized("HTTP $code")
        code == 404 -> ConnectionTestResult.TokenNotVerified(
            "Server reachable, token not verified (server too old for $PATH)",
        )
        else -> {
            val snippet = body.trim().take(120)
            ConnectionTestResult.Failed(
                if (snippet.isEmpty()) "Ping failed: HTTP $code" else "Ping failed: HTTP $code: $snippet",
            )
        }
    }
}

@Serializable
data class PingResponse(
    val ok: Boolean = false,
    @SerialName("car_id")
    val carId: String? = null,
    @SerialName("car_name")
    val carName: String? = null,
    /** Owner has an end-to-end vault: plaintext samples will be rejected. */
    @SerialName("vault_required")
    val vaultRequired: Boolean = false,
)
