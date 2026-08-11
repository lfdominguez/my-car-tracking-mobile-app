package com.domivega.gps_car.obd

/**
 * Pure helpers for ELM header/filter restore after a UDS (physical addressing) probe.
 *
 * Leaving [ATCRA] receive filters active after switching back to functional OBD (7DF)
 * makes Mode 01 look "dead" (timeouts / no decodes) while cluster UDS still worked.
 */
object ElmHeaderRestore {

    /** Typical 11-bit engine ECU positive-response CAN id (Mode 01 / physical). */
    const val ENGINE_RECEIVE_FILTER_HEX: String = "7E8"

    fun isAcceptableAtResponse(raw: String?): Boolean {
        if (raw == null) return false
        val u = raw.uppercase()
        if (u.contains("ERROR") || u.contains("UNABLE") ||
            u.contains("BUS INIT") || u.contains("STOPPED")
        ) {
            return false
        }
        // Bare "?" without OK → adapter rejected the command.
        if (u.contains("?") && !u.contains("OK")) return false
        return true
    }

    /**
     * @param receiveFilterWasSet true if this UDS probe issued ATCRA (must clear via ATAR).
     */
    fun commandsSucceeded(
        atarRaw: String?,
        atshRaw: String?,
        receiveFilterWasSet: Boolean,
    ): Boolean {
        if (!isAcceptableAtResponse(atshRaw)) return false
        if (receiveFilterWasSet) {
            return isAcceptableAtResponse(atarRaw)
        }
        return true
    }

    /**
     * After a cluster hop that set ATCRA77E, some BLE clones accept ATAR but still
     * filter only 77E — Mode 01 (7E8) stays silent on both 7DF and 7E0.
     * Force CRA to the engine response id when health is still dead.
     */
    fun shouldForceEngineReceiveFilter(
        receiveFilterWasSet: Boolean,
        healthOkAfterAtar: Boolean,
    ): Boolean = receiveFilterWasSet && !healthOkAfterAtar

    /** True when ELM returned a Mode 01 positive response for [expectPid]. */
    fun isMode01Live(raw: String?, expectPid: Int): Boolean {
        if (raw == null) return false
        val u = raw.uppercase()
        if (u.contains("NO DATA") || u.contains("UNABLE") ||
            u.contains("ERROR") || u.contains("?")
        ) {
            return false
        }
        val marker = "41" + expectPid.toString(16).uppercase().padStart(2, '0')
        return u.replace(" ", "").replace("\r", "").replace("\n", "").contains(marker)
    }
}
