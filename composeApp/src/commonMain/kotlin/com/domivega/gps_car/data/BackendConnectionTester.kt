package com.domivega.gps_car.data

/**
 * Platform API reachability + device-token check (implemented on Android via ingest client).
 */
fun interface BackendConnectionTester {
    suspend fun test(): ConnectionTestOutcome
}

sealed class ConnectionTestOutcome {
    data object Ok : ConnectionTestOutcome()
    data class Unreachable(val detail: String) : ConnectionTestOutcome()
    data class Unauthorized(val detail: String) : ConnectionTestOutcome()
    data class Failed(val detail: String) : ConnectionTestOutcome()
}
