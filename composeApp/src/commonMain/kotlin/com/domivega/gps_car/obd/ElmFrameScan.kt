package com.domivega.gps_car.obd

/**
 * Byte-aligned lookup of a `41<pid>` positive-response marker inside a stripped
 * ELM327 response.
 *
 * Taking the first `indexOf` hit anywhere in the stream lets payload bytes of a
 * long frame (e.g. odometer `A6` under `ATAL`) spoof the marker at an odd nibble
 * offset and shift every following byte by half. With `ATH0` and 11-bit CAN the
 * stream is whole bytes, so a genuine response header always sits at an even
 * offset.
 *
 * This lives in commonMain because both the value decoder (androidMain's
 * `Elm327Parser`) and the support-bitmap parser ([PidSupport]) need it, and the
 * bitmap parser silently went without — which is how a second ECU's answer
 * ended up being read as the first one's payload.
 */
object ElmFrameScan {
    /** Strip whitespace/line breaks and upper-case, as every ELM response needs. */
    fun clean(rawResponse: String): String = rawResponse
        .replace(" ", "")
        .replace("\r", "")
        .replace("\n", "")
        .replace("\t", "")
        .uppercase()

    fun positiveMarker(pidHex: String): String = "41" + pidHex.trim().uppercase().padStart(2, '0')

    /**
     * First byte-aligned occurrence of [marker], or the first occurrence at any
     * offset when no even-offset one exists, or -1. The fallback keeps a stream
     * with an odd-width artifact decoding exactly as it did before.
     */
    fun markerIndex(clean: String, marker: String): Int {
        var first = -1
        var from = 0
        while (from <= clean.length - marker.length) {
            val hit = clean.indexOf(marker, from)
            if (hit < 0) break
            if (first < 0) first = hit
            if (hit % 2 == 0) return hit
            from = hit + 1
        }
        return first
    }

    /**
     * Every byte-aligned occurrence of [marker], in order — one per responding
     * module when several answer a functional request and `ATH0` glues their
     * replies together.
     *
     * Falls back to the single first match at any offset when no even-offset one
     * exists, matching [markerIndex], so an adapter emitting odd-width output
     * degrades to the old single-frame behaviour instead of losing the frame.
     */
    fun markerIndices(clean: String, marker: String): List<Int> {
        if (marker.isEmpty()) return emptyList()
        val out = mutableListOf<Int>()
        var first = -1
        var from = 0
        while (from <= clean.length - marker.length) {
            val hit = clean.indexOf(marker, from)
            if (hit < 0) break
            if (first < 0) first = hit
            if (hit % 2 == 0) out += hit
            from = hit + 1
        }
        if (out.isEmpty() && first >= 0) return listOf(first)
        return out
    }
}
