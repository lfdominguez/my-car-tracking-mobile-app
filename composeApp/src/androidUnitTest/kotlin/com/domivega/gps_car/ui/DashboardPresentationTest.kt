package com.domivega.gps_car.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardPresentationTest {
    @Test
    fun trackingLabel_activeAndIdle() {
        assertEquals("ACTIVE TRACKING", DashboardPresentation.trackingLabel(isTracking = true))
        assertEquals("IDLE", DashboardPresentation.trackingLabel(isTracking = false))
    }

    @Test
    fun odometerLabel_unknownAndRounding() {
        assertEquals("— km", DashboardPresentation.odometerLabel(null))
        assertEquals("— km", DashboardPresentation.odometerLabel(Double.NaN))
        assertEquals("12.3 km", DashboardPresentation.odometerLabel(12.34))
        assertEquals("100 km", DashboardPresentation.odometerLabel(100.4))
    }

    @Test
    fun hvBatterySummary_joinWhenPresent() {
        assertNull(DashboardPresentation.hvBatterySummary(null, null, null))
        assertEquals("18.4 kW", DashboardPresentation.hvBatterySummary(18.44, null, null))
        assertEquals("355 V", DashboardPresentation.hvBatterySummary(null, 355.6, null))
        assertEquals(
            "18.4 kW · 355 V · 51.8 A",
            DashboardPresentation.hvBatterySummary(18.44, 355.6, 51.83),
        )
    }

    @Test
    fun hvBatterySummary_rendersChargingAsNegative() {
        // Negative current is charge going into the pack (regen or plug-in).
        assertEquals(
            "-18.4 kW · 355 V · -51.8 A",
            DashboardPresentation.hvBatterySummary(-18.44, 355.6, -51.83),
        )
        // Values that round inside the first tenth keep their sign.
        assertEquals("-0.4 kW", DashboardPresentation.hvBatterySummary(-0.44, null, null))
    }
}
