package com.domivega.gps_car.obd

/**
 * Settings applied when the user selects [VehicleObdProfile.Mg3HybridPlus].
 *
 * 2024- MG3 Hybrid+ (SAIC): 1.5 Atkinson petrol plus a 3-speed hybrid gearbox.
 * It drives on the motor for most of a city trip — a logged trip held RPM at 0 on
 * 82 % of its samples — which is why the profile forces [FuelClass.HYBRID]. Under
 * ICE rules an engine-off standstill leaves no vehicle-on proof at all, and
 * [EcuTrackingGate] then cuts the trip about 100 s later; the hybrid rules accept
 * module voltage as a keep-alive instead, which this car answers on every poll.
 *
 * Tank capacity is the hybrid's 36 L. The petrol MG3 carries 45 L, and using that
 * figure here would misprice every fuel-level cross-check on the platform.
 *
 * Known quirk, deliberately not worked around: this car's Mode 01 PID 0x2F appears
 * to run on a compressed scale. A logged brim moved it 17 % -> 71 % (raw 45 -> 181
 * of 255) and it then held raw 181 across four trips, while the driver reported the
 * dash gauge reading full — so raw 181 looks like this sender's full-tank ceiling
 * rather than a stale value, and the plateau is a float parked at its mechanical stop.
 *
 * A full tank reading under 100 % is itself normal: two other vehicles logged by this
 * app saturate at raw 236-240 (92-94 %). Only the size of the gap is unusual here, and
 * 36 L expressed against a ~50 L nominal scale would land almost exactly on 71 %.
 *
 * [FuelLevelScale] therefore rescales this profile's 0x2F so [FULL_TANK_RAW] reads
 * 100 %. The anchor is the full end only: raw 0 is assumed empty and the mapping in
 * between is linear, which is least reliable near empty where floats bend most. If a
 * second anchor is ever measured on this car — what 0x2F reads when the low-fuel
 * warning lights — the mapping should be revisited with it.
 */
object Mg3HybridDefaults {
    const val PROTOCOL = "ISO_15765_4_CAN_11_500"
    const val DISPLACEMENT_L = 1.5
    const val TANK_CAPACITY_L = 36.0

    /**
     * Fuel grade preset, overriding the E10 that [FuelClass.HYBRID] would otherwise
     * default to. The car this profile was built from runs on Mexican pump petrol,
     * which is substantially ethanol-free, and E0 carries the matching stoichiometric
     * ratio (14.7 against E10's 14.08). That ratio feeds [FuelConsumptionCalculator]
     * directly, so the wrong grade biases every L/h figure by about 4 %.
     *
     * It stays a plain preset selection, so anyone running the same car on E10 can
     * change it in Settings without leaving the profile.
     */
    const val FUEL_GRADE = "E0"

    /** See [ElmInitPolicy.singleResponderStHex]. 255 -> 1020 ms. */
    const val ST_SINGLE_RESPONDER_HEX = "FF"

    /** Raw 0x2F byte this car reports for a full tank. See [FuelLevelScale]. */
    const val FULL_TANK_RAW = 181
}
