package com.domivega.gps_car.obd

/**
 * Session dash odometer after a one-shot baseline lock (e.g. VW cluster UDS).
 * Advances with Mode 01 PID 0x31 (distance since codes cleared).
 *
 * Distance is accumulated step by step rather than measured against a fixed start
 * value, because PID 0x31 is not monotonic for the life of a session: it is two
 * bytes, so it wraps at [PID31_MAX], and it restarts at zero whenever someone
 * clears DTCs. Against a fixed start, either event makes every later reading
 * smaller than the start, the delta clamps at zero, and the odometer silently
 * stops advancing for the rest of the drive.
 *
 * A wrap is recognised and carried across. Any other decrease is treated as a
 * counter reset: nothing is added for that step and the reference moves to the
 * new value, so the odometer holds instead of jumping backwards or stalling.
 */
class SessionOdometerTracker {
    private var locked: Boolean = false
    private var baselineKm: Double = 0.0
    private var lastPid31Km: Double? = null
    private var accumulatedKm: Double = 0.0

    val isLocked: Boolean get() = locked

    fun reset() {
        locked = false
        baselineKm = 0.0
        lastPid31Km = null
        accumulatedKm = 0.0
    }

    fun lockBaseline(baselineKm: Double, pid31Km: Double?) {
        if (locked) return
        if (!baselineKm.isFinite() || baselineKm < 0.0) return
        this.baselineKm = baselineKm
        this.lastPid31Km = pid31Km?.takeIf { it.isFinite() && it >= 0.0 }
        this.accumulatedKm = 0.0
        locked = true
    }

    fun currentKm(pid31Km: Double?): Double? {
        if (!locked) return null
        val cur = pid31Km?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return baselineKm + accumulatedKm
        val last = lastPid31Km
        if (last == null) {
            lastPid31Km = cur
            return baselineKm + accumulatedKm
        }
        accumulatedKm += stepKm(last, cur)
        lastPid31Km = cur
        return baselineKm + accumulatedKm
    }

    /** Distance to add for a PID 0x31 move from [last] to [cur]. */
    private fun stepKm(last: Double, cur: Double): Double = when {
        cur >= last -> cur - last
        // Two-byte counter rolled over: carry the remainder plus the new reading.
        last >= WRAP_HIGH_KM && cur <= WRAP_LOW_KM -> (PID31_MAX + 1.0 - last) + cur
        // Codes cleared (or a bad read): hold, and re-reference to the new value.
        else -> 0.0
    }

    private companion object {
        const val PID31_MAX = 65_535.0

        /** A decrease only counts as a wrap when it straddles the counter's ends. */
        const val WRAP_HIGH_KM = 65_000.0
        const val WRAP_LOW_KM = 500.0
    }
}
