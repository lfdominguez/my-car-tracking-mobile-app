package com.domivega.gps_car.obd

/**
 * SAE J1979 Mode 01 PID 0x9A — Hybrid/EV vehicle system data (battery).
 *
 * This PID packs several signals into one response, so it cannot go through
 * [Elm327Parser]'s single-value path. It used to be decoded as a battery charge
 * percentage, which is not what it carries; the percentage-style HV signal is
 * PID 0x5B (pack remaining life).
 *
 * Layout, six data bytes, big-endian:
 * ```
 *   A       B        C D                E F
 *   flags   (rsvd)   voltage x 1/64     current x 1/10, signed
 * ```
 * - `A` bit 0      hybrid/EV mode: 0 = charge-sustaining, 1 = charge-depleting
 * - `A` bits 1..2  extended mode: 0 = CSM, 1 = CDM, 2 = CIM
 * - `C D`          pack voltage in volts, resolution 1/64, 0..1023.98 V
 * - `E F`          pack current in amps, resolution 1/10, signed,
 *                  -3276.8..3276.7 A, positive = discharge
 *
 * UNVERIFIED against the SAE text, which is paywalled. The layout comes from the
 * community OBDb/SAEJ1979 signal set and public PID references, and those sources
 * disagree on whether the PID is six or seven bytes total — the fields above sit in
 * the first six either way.
 *
 * Note that the declared ranges are structural: every possible pair of bytes decodes
 * to a value inside them, so they cannot be used to tell a real reading from a wrong
 * one. The guard that actually matters is not polling this PID on an ICE vehicle,
 * which ObdBleManager does via the powertrain setting.
 */
object HvBatteryReading {
    /** Synthetic PID keys, matching the existing `ff…` convention for derived values. */
    const val KEY_PACK_VOLT = "ffhvvolt"
    const val KEY_PACK_AMP = "ffhvamp"
    const val KEY_PACK_KW = "ffhvkw"
    const val KEY_MODE = "ffhvmode"

    const val REQUIRED_DATA_BYTES = 6

    const val MAX_PACK_VOLT = 1023.98
    const val MAX_PACK_AMP = 3276.7

    /** Widest power the voltage and current ranges can produce, for the sanity gate. */
    const val MAX_PACK_KW = 3356.0

    data class Reading(
        val packVolts: Double,
        val packAmps: Double,
        val hevMode: Int,
    ) {
        /** Positive current is discharge, so positive kW is power leaving the pack. */
        val packKw: Double get() = packVolts * packAmps / 1000.0
    }

    /**
     * @param dataHex payload hex after the `419A` marker (see Elm327Parser.dataHexFor)
     * @return decoded reading, or null when the payload is short or not hex
     */
    fun fromDataHex(dataHex: String?): Reading? {
        val hex = dataHex?.trim()?.uppercase() ?: return null
        if (hex.length < REQUIRED_DATA_BYTES * 2) return null
        val body = hex.take(REQUIRED_DATA_BYTES * 2)
        if (!body.all { it in "0123456789ABCDEF" }) return null

        val flags = body.substring(0, 2).toInt(16)
        val volts = body.substring(4, 8).toInt(16) / 64.0
        val rawAmps = body.substring(8, 12).toInt(16)
        val amps = (if (rawAmps >= 0x8000) rawAmps - 0x10000 else rawAmps) / 10.0

        return Reading(
            packVolts = volts,
            packAmps = amps,
            hevMode = (flags shr 1) and 0x03,
        )
    }

    fun modeLabel(mode: Int): String = when (mode) {
        0 -> "Charge sustaining"
        1 -> "Charge depleting"
        2 -> "Charge increasing"
        else -> "Mode $mode"
    }
}
