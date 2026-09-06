package com.domivega.gps_car.obd

/**
 * Recovery for an ELM session that stops answering while the link still looks alive.
 *
 * A hung adapter keeps its GATT link up and accepts writes, so neither
 * [ElmCommandFailure.shouldAbortPoll] (isLinked=true, writeFailed=false) nor
 * [UdsRestorePolicy.shouldHardRecoverSession] (VW cluster UDS only) can end it:
 * a real trip logged 415 consecutive Mode 01 timeouts over 27 minutes before an
 * unrelated coroutine cancellation finally forced a re-init. The adapter was
 * recoverable the whole time — it answered `ATZ` immediately on reconnect.
 *
 * This gate is protocol- and vehicle-agnostic on purpose: any adapter that hangs
 * with the link up hits it, ISO 9141-2 clones just hang most often.
 */
object ElmSessionStallPolicy {
    /**
     * Consecutive Mode 01 command timeouts before closing the link for a full re-init.
     *
     * Sized against [EcuTrackingGate.DEFAULT_STOP_GRACE_MS]: at a 4 s command timeout
     * this fires around 52 s, leaving room for a ~10 s re-init so live PIDs resume
     * inside the 90 s grace and the trip is never cut. Raising it past
     * [maxStreakWithinGrace] would let the trip end before recovery is attempted.
     */
    const val DEFAULT_TIMEOUT_STREAK: Int = 12

    /**
     * Largest streak that still recovers before the trip-stop grace expires.
     *
     * @param commandTimeoutMs per-command timeout budget, including inter-command settle.
     * @param reinitMs time a full close + ELM re-init needs before live PIDs resume.
     */
    fun maxStreakWithinGrace(
        commandTimeoutMs: Long,
        reinitMs: Long,
        graceMs: Long = EcuTrackingGate.DEFAULT_STOP_GRACE_MS,
    ): Int {
        if (commandTimeoutMs <= 0L) return 0
        val budget = graceMs - reinitMs
        if (budget <= 0L) return 0
        return (budget / commandTimeoutMs).toInt()
    }

    /**
     * True when the engine stack has been mute long enough to justify a hard re-init.
     *
     * Only sustained silence counts: [consecutiveEngineTimeouts] resets on any decoded
     * response, so a single slow round or a `NO DATA` blip never reaches the threshold.
     */
    fun shouldRecoverSession(
        consecutiveEngineTimeouts: Int,
        streak: Int = DEFAULT_TIMEOUT_STREAK,
    ): Boolean {
        if (streak <= 0) return false
        return consecutiveEngineTimeouts >= streak
    }
}
