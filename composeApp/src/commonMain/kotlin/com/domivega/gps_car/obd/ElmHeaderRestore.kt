package com.domivega.gps_car.obd

/**
 * Pure helpers for ELM header/filter restore after a UDS (physical addressing) probe.
 *
 * Leaving [ATCRA] receive filters active after switching back to functional OBD (7DF)
 * makes Mode 01 look "dead" (timeouts / no decodes) while cluster UDS still worked.
 */
object ElmHeaderRestore {

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
