package com.domivega.gps_car.obd

/**
 * Does a completed ELM frame actually answer the Mode 01 PID that was asked for?
 *
 * The transport has no request/response correlation: a frame is handed to
 * whichever command is pending when its `>` prompt arrives. After a command
 * times out, a reply that turns up later satisfies the *next* command, and the
 * real reply then satisfies the one after that — a self-sustaining one-command-off
 * cascade. The value decoder already refuses to decode a foreign frame, but the
 * miss it produces is charged to the wrong PID, inflating that PID's miss streak
 * and feeding it toward the session-disable threshold.
 */
object ElmFrameCorrelation {
    /**
     * @return false only when the frame carries a byte-aligned Mode 01 positive
     * response for a *different* PID. Anything with no positive response at all —
     * `NO DATA`, `?`, an AT echo, a negative response — is not evidence of desync
     * and stays a plain miss for the requested PID.
     */
    fun isForRequestedPid(rawResponse: String?, pidHex: String): Boolean {
        if (rawResponse.isNullOrBlank()) return true
        val clean = ElmFrameScan.clean(rawResponse)
        val wanted = ElmFrameScan.positiveMarker(pidHex)
        if (ElmFrameScan.markerIndices(clean, wanted).isNotEmpty()) return true
        return !containsForeignMode01Response(clean, wanted)
    }

    private fun containsForeignMode01Response(clean: String, wanted: String): Boolean {
        var i = 0
        while (i + 4 <= clean.length) {
            if (clean.startsWith("41", i) && clean.regionMatches(i, wanted, 0, 4).not()) {
                val pidChars = clean.substring(i + 2, i + 4)
                if (pidChars.all { it in "0123456789ABCDEF" }) return true
            }
            i += 2
        }
        return false
    }
}
