package com.domivega.gps_car.obd

/**
 * When and how routine Mode 01 polling moves from the functional header (7DF,
 * every module answers) to the engine's physical header (7E0, one responder).
 *
 * The adoption probe used to be a single `010C`. A Toyota Corolla log showed the
 * ECU going momentarily mute right at that instant — `0c`, `0d` and `42` all
 * missed on 7DF one second later too — so one unlucky moment pinned the whole
 * session to the slower multi-responder header. Retries with an alternate probe
 * PID, plus a mid-session re-probe, make that transient cost nothing.
 */
object PhysicalHeaderPolicy {
    const val PROBE_ATTEMPTS: Int = 4

    /** Whole-probe budget, so a car with no 7E0 cannot stretch init by 16 s. */
    const val PROBE_BUDGET_MS: Long = 6_000L

    /** Rounds between mid-session re-probes while still stuck on the functional header. */
    const val REPROBE_EVERY_ROUNDS: Int = 40

    /** Successful engine decodes required before a re-probe: the bus must be alive first. */
    const val REPROBE_MIN_ENGINE_OK: Int = 4

    fun probeBackoffMs(attempt: Int): Long = 250L + attempt * 250L

    /**
     * Alternate the probe PID so one PID's momentary silence cannot decide the
     * session. RPM and coolant are both Mode 01 staples answered by the engine.
     */
    fun probePidHex(attempt: Int): String = if (attempt % 2 == 0) "0c" else "05"

    /**
     * Whether to pin an `ATCRA` receive filter alongside the physical header.
     *
     * False for [VehicleObdProfile.VwMqb]: that profile hops the cluster with
     * `ATCRA77E` and has its own conditional restore path, and a second filter
     * owner in the same session is not worth the interaction risk.
     */
    fun shouldApplyReceiveFilter(profile: VehicleObdProfile): Boolean =
        profile != VehicleObdProfile.VwMqb

    fun shouldReprobe(
        round: Int,
        lastProbeRound: Int,
        onFunctionalHeader: Boolean,
        engineOkCount: Int,
        everyRounds: Int = REPROBE_EVERY_ROUNDS,
        minEngineOk: Int = REPROBE_MIN_ENGINE_OK,
    ): Boolean {
        if (!onFunctionalHeader) return false
        if (engineOkCount < minEngineOk) return false
        if (everyRounds < 1) return false
        return round - lastProbeRound >= everyRounds
    }
}
