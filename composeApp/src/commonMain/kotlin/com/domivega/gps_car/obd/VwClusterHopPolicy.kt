package com.domivega.gps_car.obd

/**
 * ELM hop policy for VW MQB instrument cluster UDS (request [REQUEST_HEADER_HEX]).
 *
 * Road log on Nivus Highline 2024: DID 0x2203 odometer is multi-frame ISO-TP.
 * The path that once returned km used ATCRA77E + ATFCSM1. Leaving CRA sticky
 * breaks Mode 01 (7E8 filtered out) — filter is hop-local and must ATAR-clear.
 *
 * Custom FC must retry after **any** plain miss (including `NO DATA`), not only
 * incomplete multi-frame fragments: many clones emit plain NO DATA when CF
 * never arrives without tester FC.
 */
object VwClusterHopPolicy {
    const val REQUEST_HEADER_HEX: String = "714"
    const val RESPONSE_FILTER_HEX: String = "77E"
    const val USE_RECEIVE_FILTER: Boolean = true

    /**
     * @param gotOdometerKm true if plain (adapter-default FC) pass already published km
     * @param lastRaw optional last ELM text (unused for gate; kept for call-site clarity/logs)
     */
    fun shouldRetryWithCustomFlowControl(
        gotOdometerKm: Boolean,
        lastRaw: String? = null,
    ): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = lastRaw
        return !gotOdometerKm
    }

    /**
     * Cluster fuel/oil/doors extras share the hop (still on CRA77E). Skip on odo miss
     * so failed probes do not burn multi-second UDS timeouts before header restore.
     */
    fun shouldPollClusterExtras(gotOdometerKm: Boolean): Boolean = gotOdometerKm
}
