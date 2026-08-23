package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetrySpikeFilterTest {
    @Test
    fun drops200KphHickup() {
        val f = TelemetrySpikeFilter()
        f.accept(80.0, 1800.0, 0L)
        val mid = f.accept(200.0, 8000.0, 1000L)
        assertTrue(mid.speedKph!! < 100.0)
        assertTrue(mid.rpm!! < 2500.0)
        val next = f.accept(82.0, 1850.0, 2000L)
        assertEquals(82.0, next.speedKph!!, 0.01)
    }

    @Test
    fun keepsRealAcceleration() {
        val f = TelemetrySpikeFilter()
        f.accept(20.0, 1500.0, 0L)
        val mid = f.accept(50.0, 2200.0, 2000L)
        assertEquals(50.0, mid.speedKph!!, 0.01)
        val last = f.accept(80.0, 2500.0, 4000L)
        assertEquals(80.0, last.speedKph!!, 0.01)
    }

    @Test
    fun replaces240HickupWithGps() {
        val f = TelemetrySpikeFilter()
        f.accept(80.0, 1800.0, 0L)
        val mid = f.accept(240.0, 1800.0, 1000L, gpsSpeedKph = 82.0)
        assertEquals(82.0, mid.speedKph!!, 0.01)
        val next = f.accept(83.0, 1850.0, 2000L, gpsSpeedKph = 83.0)
        assertEquals(83.0, next.speedKph!!, 0.01)
    }

    @Test
    fun replacesInstantZeroWithGps() {
        val f = TelemetrySpikeFilter()
        f.accept(80.0, 1800.0, 0L)
        val mid = f.accept(0.0, 1800.0, 1000L, gpsSpeedKph = 79.0)
        assertEquals(79.0, mid.speedKph!!, 0.01)
    }

    @Test
    fun replacesStuckObdZeroWhileGpsMoving() {
        val f = TelemetrySpikeFilter()
        f.accept(0.0, 800.0, 0L)
        val mid = f.accept(0.0, 800.0, 1000L, gpsSpeedKph = 45.0)
        assertEquals(45.0, mid.speedKph!!, 0.01)
    }

    @Test
    fun keepsObdWhenGpsAgrees() {
        val f = TelemetrySpikeFilter()
        f.accept(80.0, 1800.0, 0L)
        val mid = f.accept(82.0, 1850.0, 1000L, gpsSpeedKph = 78.0)
        assertEquals(82.0, mid.speedKph!!, 0.01)
    }

    @Test
    fun usesGpsWhenObdSpeedMissing() {
        val f = TelemetrySpikeFilter()
        f.accept(80.0, 1800.0, 0L)
        val mid = f.accept(null, 1800.0, 1000L, gpsSpeedKph = 76.0)
        assertEquals(76.0, mid.speedKph!!, 0.01)
    }

    @Test
    fun ignoresInvalidGpsAndHoldsLastGood() {
        val f = TelemetrySpikeFilter()
        f.accept(80.0, 1800.0, 0L)
        val mid = f.accept(240.0, 1800.0, 1000L, gpsSpeedKph = Double.NaN)
        assertEquals(80.0, mid.speedKph!!, 0.01)
    }
}
