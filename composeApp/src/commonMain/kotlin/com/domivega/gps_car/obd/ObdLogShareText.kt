package com.domivega.gps_car.obd

/** Plain-text dump of the in-app OBD debug log (same line format as the Debug Console). */
fun formatObdLogAsText(entries: List<ObdLogEntry>): String = buildString {
    appendLine("GPS Car Tracking OBD debug log")
    appendLine()
    if (entries.isEmpty()) {
        appendLine("(no events)")
    } else {
        entries.forEach { entry ->
            appendLine(
                "${formatObdLogTimeUtc(entry.timestampMs)} ${entry.level.name.take(1)} ${entry.message}",
            )
        }
    }
}

/** UTC wall-clock HH:mm:ss from epoch ms (portable commonMain). */
fun formatObdLogTimeUtc(ms: Long): String {
    val s = ((ms / 1000) % 86_400).toInt()
    val hh = (s / 3600).toString().padStart(2, '0')
    val mm = ((s % 3600) / 60).toString().padStart(2, '0')
    val ss = (s % 60).toString().padStart(2, '0')
    return "$hh:$mm:$ss"
}
