package com.domivega.gps_car.obd

/**
 * ELM327 init extras that depend on vehicle profile.
 * Golf mk4 TDI is K-line: do not send CAN functional address ATSH7DF.
 */
object ElmInitPolicy {
    fun sendCanFunctionalHeader(profile: VehicleObdProfile): Boolean =
        profile != VehicleObdProfile.VwGolfMk4Tdi

    /**
     * ELM `ST` ceiling once polling is pinned to a physical header.
     *
     * MG routes OBD through a Gateway Module, and MG owners running other scan
     * tools report the stock ceiling returning garbage until the timer is widened.
     * This is the tier where widening is nearly free: on a single responder
     * [ElmPerformanceMode.mode01PollCommand] appends the expected-response-count
     * suffix, so the adapter returns on the first frame and only a PID with no
     * answer at all ever pays the full window.
     *
     * The functional-header tier is deliberately left alone. There the adapter
     * waits the whole window after every reply looking for a second responder, so
     * widening it would cost every poll of every round.
     *
     * Unverified on the MG3 Hybrid+ itself — the reports come from MG EVs, whose
     * gateway is not known to be the same part.
     */
    fun singleResponderStHex(profile: VehicleObdProfile): String =
        when (profile) {
            VehicleObdProfile.Mg3HybridPlus -> Mg3HybridDefaults.ST_SINGLE_RESPONDER_HEX
            else -> ElmPerformanceMode.ST_SINGLE_RESPONDER_HEX
        }
}
