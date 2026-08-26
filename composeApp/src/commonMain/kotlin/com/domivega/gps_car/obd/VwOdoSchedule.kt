package com.domivega.gps_car.obd

/**
 * When to run VW MQB cluster UDS odometer hops:
 * - one initial after the engine stack is healthy
 * - then once per stop after continuous dwell at ~0 km/h, until [markLocked]
 * - after a successful odometer lock via [markLocked], no further hops until [reset]
 * Never on a fixed interval while moving.
 *
 * The hop is deliberately rare. Reaching a cluster DID means pointing the ELM at
 * ATSH714 with a receive filter to issue UDS $22, and on this platform that breaks
 * Mode 01 afterwards — the engine stack goes silent and has to be nursed back by
 * ElmHeaderRestore. So the hop happens only until an odometer baseline is captured,
 * after which Mode 01 PID 0x31 (distance since codes cleared) carries the odometer
 * for the rest of the session and the header never has to move again.
 */
class VwOdoSchedule(
    private val engineOkMin: Int = 8,
    private val stopDwellMs: Long = 5_000L,
    private val stopSpeedMaxKmh: Double = 0.5,
) {
    data class TickResult(val shouldHop: Boolean, val isInitial: Boolean = false)

    private var initialDone: Boolean = false
    private var stopHopDone: Boolean = false
    private var stopDwellStartMs: Long? = null
    private var hopInFlight: Boolean = false
    /** Last known stopped state (for consuming stop on hop finish). */
    private var lastWasStopped: Boolean = false
    /** True after the first published cluster odometer. */
    private var locked: Boolean = false

    val isLocked: Boolean get() = locked

    fun reset() {
        initialDone = false
        stopHopDone = false
        stopDwellStartMs = null
        hopInFlight = false
        lastWasStopped = false
        locked = false
    }

    /**
     * Call when cluster odometer km was published. The baseline is captured, so no
     * further hop is worth risking the Mode 01 stream: disables hops until [reset].
     */
    fun markLocked() {
        locked = true
        hopInFlight = false
        initialDone = true
    }

    fun onPollTick(
        nowMs: Long,
        speedKmh: Double?,
        engineOkCount: Int,
        udsNotBeforeMs: Long,
    ): TickResult {
        if (hopInFlight || locked) return TickResult(false)

        updateMotion(nowMs, speedKmh)

        if (udsNotBeforeMs != 0L && nowMs < udsNotBeforeMs) {
            return TickResult(false)
        }

        if (!initialDone && engineOkCount >= engineOkMin) {
            hopInFlight = true
            return TickResult(shouldHop = true, isInitial = true)
        }

        if (initialDone && !stopHopDone && speedKmh != null && isStopped(speedKmh)) {
            val start = stopDwellStartMs
            if (start != null && nowMs - start >= stopDwellMs) {
                hopInFlight = true
                return TickResult(shouldHop = true, isInitial = false)
            }
        }
        return TickResult(false)
    }

    /**
     * Call after the hop completes (success or fail). Fail still consumes the current
     * stop slot when the hop ran while stopped; initial while moving does not block
     * a later stop hop.
     */
    fun onHopFinished(success: Boolean) {
        hopInFlight = false
        @Suppress("UNUSED_PARAMETER")
        val ignored = success
        initialDone = true
        if (lastWasStopped) {
            stopHopDone = true
        }
    }

    private fun updateMotion(nowMs: Long, speedKmh: Double?) {
        if (speedKmh == null) {
            stopDwellStartMs = null
            lastWasStopped = false
            return
        }
        if (isStopped(speedKmh)) {
            lastWasStopped = true
            if (stopDwellStartMs == null) stopDwellStartMs = nowMs
        } else {
            lastWasStopped = false
            stopDwellStartMs = null
            stopHopDone = false
        }
    }

    private fun isStopped(speedKmh: Double): Boolean = speedKmh <= stopSpeedMaxKmh
}
