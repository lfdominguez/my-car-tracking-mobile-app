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
    fun clusterExtras_joinWhenPresent() {
        assertNull(DashboardPresentation.clusterExtras(null, null))
        assertEquals("Oil 92°C", DashboardPresentation.clusterExtras(92.4, null))
        assertEquals("Doors closed", DashboardPresentation.clusterExtras(null, "Doors closed"))
        assertEquals("Oil 90°C · Doors closed", DashboardPresentation.clusterExtras(90.0, "Doors closed"))
    }
}
