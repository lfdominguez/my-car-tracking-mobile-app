package com.domivega.gps_car.obd

/**
 * VW MQB product rule: obtain cluster odometer before Mode 01 poll starts.
 * Non-VW profiles skip the gate.
 */
object VwOdoFirstGate {
    const val RETRY_DELAY_MS: Long = 2_000L

    fun requiresOdometerBeforeMode01(profile: VehicleObdProfile): Boolean =
        profile == VehicleObdProfile.VwMqb

    /** How recently the engine must have answered for an odometer lock to survive a re-init. */
    const val LOCK_CARRY_MS: Long = 5 * 60_000L

    /**
     * A stall re-init mid-trip used to throw the lock away and block Mode 01 on a
     * fresh cluster read, which took 8 attempts (~50 s without RPM/speed) on a real
     * trip. A recent lock is still right: PID 0x31 carries it across the gap.
     */
    fun keepLockAcrossReinit(locked: Boolean, lastLiveAtMs: Long, nowMs: Long): Boolean =
        locked && lastLiveAtMs > 0L && nowMs - lastLiveAtMs in 0L..LOCK_CARRY_MS

    fun retryDelayMs(attemptIndex: Int): Long {
        @Suppress("UNUSED_PARAMETER")
        val ignored = attemptIndex.coerceAtLeast(0)
        return RETRY_DELAY_MS
    }
}
