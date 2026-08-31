package com.domivega.gps_car.obd

/**
 * Per-profile correction for Mode 01 PID 0x2F.
 *
 * The SAE decode is `A * 100 / 255`, which assumes the sender uses the whole byte —
 * a full tank at raw 255. Real senders saturate lower: two vehicles logged by this
 * app top out at raw 236-240 (92-94 %), which is close enough to leave alone. Some
 * ECUs are calibrated far below that, and then the raw decode understates every
 * reading by a constant factor, misprices the platform's litre cross-check, and
 * shows a brimmed tank as two-thirds full.
 *
 * A profile opts in by declaring the raw byte its sender reports for a full tank.
 * The mapping is linear from a single anchor: raw 0 stays empty, the declared raw
 * becomes 100 %, and anything above it clamps. That assumption is worth stating
 * plainly — the bottom of the scale is unverified, senders are least linear near
 * empty, and the correction is therefore most trustworthy near full. It is applied
 * once, where the PID is decoded, so the gauge, the uploaded sample and the platform
 * can never disagree about the same tank.
 */
object FuelLevelScale {
    /**
     * Raw 0x2F byte this profile's sender reports for a full tank, or null to relay
     * the SAE decode unchanged.
     *
     * MG3 Hybrid+: measured at 181. A logged brim moved the sender to raw 181 while
     * the driver reported the dash gauge reading full, and it held exactly there for
     * four trips afterwards, which is what a float resting on its mechanical stop
     * looks like. 36 L expressed against a ~50 L nominal scale lands on almost the
     * same number, so a family calibration carried over to a smaller tank is the
     * likely cause.
     */
    fun fullTankRawFor(profile: VehicleObdProfile): Int? = when (profile) {
        VehicleObdProfile.Mg3HybridPlus -> Mg3HybridDefaults.FULL_TANK_RAW
        else -> null
    }

    /** @return [reportedPct] rescaled so this profile's full-tank raw reads 100 %. */
    fun correctedPct(reportedPct: Double, profile: VehicleObdProfile): Double {
        if (!reportedPct.isFinite()) return reportedPct
        val fullRaw = fullTankRawFor(profile) ?: return reportedPct
        if (fullRaw !in 1..255) return reportedPct
        return (reportedPct * 255.0 / fullRaw).coerceIn(0.0, 100.0)
    }
}
