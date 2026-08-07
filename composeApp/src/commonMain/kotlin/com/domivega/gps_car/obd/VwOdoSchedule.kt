package com.domivega.gps_car.obd

/**
 * When to run VW MQB cluster UDS odometer hops:
 * - one initial after engine stack is healthy
 * - then once per stop after continuous dwell at ~0 km/h
 * Never on a fixed interval while moving.
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

    fun reset() {
        initialDone = false
        stopHopDone = false
        stopDwellStartMs = null
        hopInFlight = false
        lastWasStopped = false
    }

    fun onPollTick(
        nowMs: Long,
        speedKmh: Double?,
        engineOkCount: Int,
        udsNotBeforeMs: Long,
    ): TickResult {
        if (hopInFlight) return TickResult(false)

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
