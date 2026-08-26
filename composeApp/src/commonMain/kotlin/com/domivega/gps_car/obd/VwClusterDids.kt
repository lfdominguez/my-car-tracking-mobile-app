package com.domivega.gps_car.obd

/**
 * VW MQB-ish instrument cluster UDS DIDs (header 0x714), beyond odometer.
 * Layouts are best-effort / UNVERIFIED until confirmed on a given vehicle.
 */
object VwClusterDids {
    const val DID_FUEL_LEVEL = 0x2206
    const val DID_OIL_TEMP = 0x202F
    const val DID_DOOR_STATUS = 0x220D

    const val KEY_FUEL_PCT = "ffuelpct"
    const val KEY_OIL_C = "ffoilc"
    const val KEY_DOORS = "ffdoors"

    /** Fixed extras after odometer on the same cluster hop. */
    val EXTRA_DIDS: List<Int> = listOf(DID_FUEL_LEVEL, DID_OIL_TEMP, DID_DOOR_STATUS)

    fun keyForDid(did: Int): String? = when (did) {
        DID_FUEL_LEVEL -> KEY_FUEL_PCT
        DID_OIL_TEMP -> KEY_OIL_C
        DID_DOOR_STATUS -> KEY_DOORS
        else -> null
    }

    /**
     * Mode 01-style `A * 100 / 255`.
     *
     * The old "else take raw A as a percent" fallback was unreachable — `A*100/255`
     * lands in 0..100 for every possible byte — and it advertised a false safety
     * net. Nothing here can tell a real fuel byte from an unrelated one, so treat a
     * result from an unconfirmed DID as a candidate, not a fact:
     * [FuelLevelReading] cross-checks it against SAE PID 0x2F before preferring it.
     */
    fun decodeFuelPercent(payload: ByteArray): Double? {
        if (payload.isEmpty()) return null
        val a = payload[0].toInt() and 0xFF
        return a * 100.0 / 255.0
    }

    fun decodeOilTempC(payload: ByteArray): Double? {
        if (payload.isEmpty()) return null
        val c = (payload[0].toInt() and 0xFF) - 40.0
        if (c in -40.0..200.0) return c
        return null
    }

    fun decodeDoorBitfield(payload: ByteArray): Long? {
        if (payload.isEmpty()) return null
        val n = minOf(4, payload.size)
        var v = 0L
        for (i in 0 until n) {
            v = (v shl 8) or (payload[i].toInt() and 0xFF).toLong()
        }
        return v
    }

    fun decodeValue(did: Int, payload: ByteArray): Double? = when (did) {
        DID_FUEL_LEVEL -> decodeFuelPercent(payload)
        DID_OIL_TEMP -> decodeOilTempC(payload)
        DID_DOOR_STATUS -> decodeDoorBitfield(payload)?.toDouble()
        else -> null
    }

    /**
     * UNVERIFIED MQB-ish door bit labels (low bits of bitfield after pack).
     * bit0=Driver, bit1=Passenger, bit2=RL, bit3=RR, bit4=Boot, bit5=Hood.
     */
    fun doorSummary(bitfield: Long): String {
        if (bitfield == 0L) return "Doors closed"
        val labels = listOf(
            0 to "Driver",
            1 to "Passenger",
            2 to "Rear left",
            3 to "Rear right",
            4 to "Boot",
            5 to "Hood",
        )
        val open = labels.mapNotNull { (bit, name) ->
            if ((bitfield and (1L shl bit)) != 0L) name else null
        }
        return if (open.isNotEmpty()) {
            open.joinToString(separator = ", ", postfix = " open")
        } else {
            "Doors 0x${bitfield.toString(16).uppercase()}"
        }
    }

    fun doorSummaryFromPidValues(pidValues: Map<String, Double>): String? {
        val raw = pidValues[KEY_DOORS] ?: return null
        if (!raw.isFinite()) return null
        return doorSummary(raw.toLong())
    }
}
