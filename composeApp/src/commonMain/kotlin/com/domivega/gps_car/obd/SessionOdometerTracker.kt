package com.domivega.gps_car.obd

/**
 * Session dash odometer after a one-shot baseline lock (e.g. VW cluster UDS).
 * Advances with Mode 01 PID 0x31 delta: baseline + max(0, pid31 − start31).
 */
class SessionOdometerTracker {
    private var locked: Boolean = false
    private var baselineKm: Double = 0.0
    private var startPid31Km: Double? = null

    val isLocked: Boolean get() = locked

    fun reset() {
        locked = false
        baselineKm = 0.0
        startPid31Km = null
    }

    fun lockBaseline(baselineKm: Double, pid31Km: Double?) {
        if (locked) return
        if (!baselineKm.isFinite() || baselineKm < 0.0) return
        this.baselineKm = baselineKm
        this.startPid31Km = pid31Km?.takeIf { it.isFinite() && it >= 0.0 }
        locked = true
    }

    fun currentKm(pid31Km: Double?): Double? {
        if (!locked) return null
        val start = startPid31Km
        val cur = pid31Km?.takeIf { it.isFinite() && it >= 0.0 }
        if (start == null) {
            if (cur != null) startPid31Km = cur
            return baselineKm
        }
        if (cur == null) return baselineKm
        val delta = (cur - start).coerceAtLeast(0.0)
        return baselineKm + delta
    }
}
