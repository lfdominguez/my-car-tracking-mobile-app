package com.domivega.gps_car.obd

/**
 * Policy after a VW cluster UDS hop when Mode 01 health is unclear.
 *
 * Forcing full GATT/ELM re-init ends [ecuConnected] and used to split trips;
 * prefer soft backoff of further UDS while Mode 01 keeps polling.
 */
object UdsRestorePolicy {
    /** How many consecutive Mode 01 command timeouts before a hard session recovery. */
    const val HARD_RECOVERY_TIMEOUT_STREAK: Int = 8

    /**
     * Hard recovery (close GATT / full re-init) only after sustained Mode 01 death,
     * not after a single post-UDS health miss.
     */
    fun shouldHardRecoverSession(
        udsRestoreUnhealthy: Boolean,
        consecutiveEngineTimeouts: Int,
        hardStreak: Int = HARD_RECOVERY_TIMEOUT_STREAK,
    ): Boolean {
        if (!udsRestoreUnhealthy) return false
        return consecutiveEngineTimeouts >= hardStreak
    }
}
