package com.domivega.gps_car.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsProviderSelectorTest {

    @Test
    fun `api 31 prefers fused when enabled`() {
        assertEquals(
            GpsProviderSelector.PROVIDER_FUSED,
            GpsProviderSelector.select(sdkInt = 31, fusedEnabled = true, gpsEnabled = true),
        )
    }

    @Test
    fun `api 31 falls back to gps when fused disabled`() {
        assertEquals(
            GpsProviderSelector.PROVIDER_GPS,
            GpsProviderSelector.select(sdkInt = 31, fusedEnabled = false, gpsEnabled = true),
        )
    }

    @Test
    fun `api 30 uses gps even if fused flag true`() {
        assertEquals(
            GpsProviderSelector.PROVIDER_GPS,
            GpsProviderSelector.select(sdkInt = 30, fusedEnabled = true, gpsEnabled = true),
        )
    }

    @Test
    fun `api 31 returns null when both disabled`() {
        assertNull(
            GpsProviderSelector.select(sdkInt = 31, fusedEnabled = false, gpsEnabled = false),
        )
    }

    @Test
    fun `api 24 returns null when gps disabled`() {
        assertNull(
            GpsProviderSelector.select(sdkInt = 24, fusedEnabled = false, gpsEnabled = false),
        )
    }
}
