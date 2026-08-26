package com.domivega.gps_car.obd

class Elm327Parser {
    fun decodePid(pidHex: String, rawResponse: String): Double? {
        if (rawResponse.isBlank()) return null
        if (rawResponse.contains("NO DATA", ignoreCase = true) ||
            rawResponse.contains("ERROR", ignoreCase = true)
        ) {
            return null
        }

        val cleanResponse = rawResponse
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .uppercase()
        val normalizedPid = pidHex.uppercase()
        val searchPattern = "41$normalizedPid"

        val index = markerIndex(cleanResponse, searchPattern)
        if (index < 0) return null

        val dataPart = cleanResponse.substring(index + searchPattern.length)

        return try {
            calculateValue(normalizedPid, dataPart)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Payload hex following `41<pid>`, or null when the response holds no
     * byte-aligned positive frame for [pidHex].
     *
     * Exposed for PIDs whose payload carries several fields and so cannot use
     * [decodePid]'s single-value path — PID 0x9A, decoded by [HvBatteryReading].
     */
    fun dataHexFor(pidHex: String, rawResponse: String): String? {
        if (rawResponse.isBlank()) return null
        if (rawResponse.contains("NO DATA", ignoreCase = true) ||
            rawResponse.contains("ERROR", ignoreCase = true)
        ) {
            return null
        }
        val clean = rawResponse
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .uppercase()
        val marker = "41" + pidHex.uppercase()
        val index = markerIndex(clean, marker)
        if (index < 0) return null
        return clean.substring(index + marker.length).ifEmpty { null }
    }

    /** Byte-aligned `41<pid>` lookup, shared with [PidSupport] via [ElmFrameScan]. */
    private fun markerIndex(clean: String, marker: String): Int =
        ElmFrameScan.markerIndex(clean, marker)

    /**
     * Data bytes each PID needs for a complete decode. A short payload means the
     * frame was truncated (partial BLE/SPP read, timeout drain) — decoding it would
     * silently pad the missing bytes with 0 and publish a plausible-looking wrong
     * value, e.g. `410C1A` -> 1664 RPM. Unknown PIDs fall through to `else -> null`.
     */
    private fun requiredDataBytes(pid: String): Int = when (pid) {
        "0C", "42", "1F", "44", "31", "10", "5E", "43" -> 2
        "A6" -> 4
        else -> 1
    }

    private fun calculateValue(pid: String, data: String): Double? {
        if (data.length < requiredDataBytes(pid) * 2) return null

        val a = data.substring(0, 2).toInt(16)
        val b = if (data.length >= 4) data.substring(2, 4).toInt(16) else 0
        val c = if (data.length >= 6) data.substring(4, 6).toInt(16) else 0
        val d = if (data.length >= 8) data.substring(6, 8).toInt(16) else 0

        return when (pid) {
            "0C" -> ((a * 256.0) + b) / 4.0 // RPM
            "0D" -> a.toDouble() // Speed
            "04", "2F", "45", "49" -> (a * 100.0) / 255.0 // Percent style
            // SAE J1979 PID 0x43: absolute load value — TWO bytes, 0..25700 %.
            // Decoding only byte A published 0.0 % for every load at or below 100 %,
            // because raw = pct*255/100 never leaves the low byte until ~100.4 %.
            "43" -> ((a * 256.0) + b) * 100.0 / 255.0
            "05", "0F", "46" -> a.toDouble() - 40.0 // Temp A-40
            "0B", "33" -> a.toDouble() // MAP / Pressure A
            "42" -> ((a * 256.0) + b) / 1000.0 // Voltage
            "1F" -> (a * 256.0) + b // Runtime
            "44" -> ((a * 256.0) + b) / 32768.0 // Lambda
            "06", "07" -> (a - 128.0) * 100.0 / 128.0 // Fuel trim
            // SAE J1979 PID 0x31: distance traveled since codes cleared (km), NOT dash odometer.
            "31" -> (a * 256.0) + b
            // SAE J1979 PID 0xA6: vehicle odometer reading (km), 4 bytes / 10.
            "A6" -> {
                val raw =
                    (a.toLong() shl 24) or
                        (b.toLong() shl 16) or
                        (c.toLong() shl 8) or
                        d.toLong()
                raw / 10.0
            }
            "10" -> ((a * 256.0) + b) / 100.0 // MAF
            // SAE J1979 PID 0x5E: engine fuel rate (L/h)
            "5E" -> ((a * 256.0) + b) * 0.05
            // SAE J1979 PID 0x5B: hybrid battery pack REMAINING LIFE (%), not state
            // of charge — on many vehicles it tracks pack health, not charge.
            "5B" -> (a * 100.0) / 255.0
            // PID 0x9A is deliberately absent: it is "Hybrid/EV Vehicle System Data"
            // (mode bit flags in A, pack voltage in C-D /64, pack current in E-F /10),
            // NOT a percentage. Decoding it as one published a meaningless number as
            // battery SoC. Restore only with a verified multi-field reading.
            else -> null
        }
    }
}
