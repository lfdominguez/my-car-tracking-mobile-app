package com.domivega.gps_car.obd

/**
 * Per-window per-PID success tally for the throughput log line.
 *
 * The manager logs `PID xx miss (...)` only once per PID per session, so a log
 * could prove that half of all requests were failing without saying which PIDs
 * were failing. This renders the worst offenders inline with the existing 60 s
 * rate line, on the same window, which is what makes a shared log conclusive.
 */
object ObdPollStats {
    const val DEFAULT_TOP_N: Int = 8

    /**
     * @return `worst=49:2/14 2f:3/12` — ok-over-attempts per PID, worst ratio
     * first, or an empty string when nothing was attempted or nothing missed.
     */
    fun formatMissTally(
        ok: Map<String, Int>,
        miss: Map<String, Int>,
        topN: Int = DEFAULT_TOP_N,
    ): String {
        if (topN < 1) return ""
        val rows = (ok.keys + miss.keys).mapNotNull { pid ->
            val okCount = ok[pid] ?: 0
            val missCount = miss[pid] ?: 0
            val attempts = okCount + missCount
            if (attempts == 0 || missCount == 0) return@mapNotNull null
            Row(pid.lowercase(), okCount, attempts, missCount.toDouble() / attempts)
        }
        if (rows.isEmpty()) return ""
        val worst = rows
            // Ratio first, then attempts: a 1/1 miss outranks nothing, but a PID
            // that failed 11 of 12 tries is the one worth naming.
            .sortedWith(compareByDescending<Row> { it.missRatio }.thenByDescending { it.attempts })
            .take(topN)
        return worst.joinToString(separator = " ", prefix = "worst=") {
            "${it.pid}:${it.ok}/${it.attempts}"
        }
    }

    private class Row(
        val pid: String,
        val ok: Int,
        val attempts: Int,
        val missRatio: Double,
    )
}
