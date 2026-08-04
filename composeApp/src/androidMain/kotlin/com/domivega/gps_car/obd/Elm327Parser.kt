package com.domivega.gps_car.obd

class Elm327Parser {
    fun decodePid(pidHex: String, rawResponse: String): Double? {
        if (rawResponse.isBlank()) return null
        if (rawResponse.contains("NO DATA") || rawResponse.contains("ERROR")) return null

        val cleanResponse = rawResponse.replace(" ", "").replace("\r", "").replace("\n", "")
        val normalizedPid = pidHex.uppercase()
        val searchPattern = "41$normalizedPid"
        
        val index = cleanResponse.indexOf(searchPattern, ignoreCase = true)
        if (index == -1) return null

        val dataPart = cleanResponse.substring(index + searchPattern.length)
        
        return try {
            calculateValue(normalizedPid, dataPart)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateValue(pid: String, data: String): Double? {
        if (data.length < 2) return null
        
        val a = data.substring(0, 2).toInt(16)
        val b = if (data.length >= 4) data.substring(2, 4).toInt(16) else 0
        val c = if (data.length >= 6) data.substring(4, 6).toInt(16) else 0
        val d = if (data.length >= 8) data.substring(6, 8).toInt(16) else 0
        
        return when (pid) {
            "0C" -> ((a * 256.0) + b) / 4.0 // RPM
            "0D" -> a.toDouble() // Speed
            "04", "43", "2F", "45", "49" -> (a * 100.0) / 255.0 // Percent style
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
                if (data.length < 8) return null
                val raw =
                    (a.toLong() shl 24) or
                        (b.toLong() shl 16) or
                        (c.toLong() shl 8) or
                        d.toLong()
                raw / 10.0
            }
            "10" -> ((a * 256.0) + b) / 100.0 // MAF
            else -> null
        }
    }
}
