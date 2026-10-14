package com.domivega.gps_car.data

/**
 * Platform API reachability + device-token check (implemented on Android via ingest client).
 */
fun interface BackendConnectionTester {
    suspend fun test(): ConnectionTestOutcome
}

sealed class ConnectionTestOutcome {
    /** Token accepted by `GET /api/track/ping`. */
    data class Ok(val carName: String? = null) : ConnectionTestOutcome()
    data class Unreachable(val detail: String) : ConnectionTestOutcome()
    data class Unauthorized(val detail: String) : ConnectionTestOutcome()
    /** Server reachable but too old for the ping endpoint, so the token was not checked. */
    data class TokenNotVerified(val detail: String) : ConnectionTestOutcome()
    data class Failed(val detail: String) : ConnectionTestOutcome()
}
