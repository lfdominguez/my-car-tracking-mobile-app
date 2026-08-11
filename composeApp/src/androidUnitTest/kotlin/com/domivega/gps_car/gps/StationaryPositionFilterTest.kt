package com.domivega.gps_car.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationaryPositionFilterTest {

    @Test
    fun `gpsSpeedToKph converts meters per second`() {
        assertEquals(3.6, StationaryPositionFilter.gpsSpeedToKph(1.0), 1e-9)
        assertEquals(0.0, StationaryPositionFilter.gpsSpeedToKph(0.0), 1e-9)
    }

    @Test
    fun `first sample while stopped is accepted as anchor`() {
        val filter = StationaryPositionFilter()
        val result = filter.accept(
            latitude = 40.0,
            longitude = -3.0,
            accuracy = 5.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = null,
        )
        assertEquals(40.0, result.latitude, 1e-9)
        assertEquals(-3.0, result.longitude, 1e-9)
        assertEquals(5.0, result.accuracy, 1e-9)
        assertFalse(result.frozen)
    }

    @Test
    fun `obd zero after good fix freezes last good position and accuracy`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.1, -3.1, 4.0, obdSpeedKph = 30.0, gpsSpeedMps = null)

        val frozen = filter.accept(
            latitude = 40.9,
            longitude = -3.9,
            accuracy = 25.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = null,
        )

        assertEquals(40.1, frozen.latitude, 1e-9)
        assertEquals(-3.1, frozen.longitude, 1e-9)
        assertEquals(4.0, frozen.accuracy, 1e-9)
        assertTrue(frozen.frozen)
    }

    @Test
    fun `obd above threshold unfreezes and updates anchor`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.1, -3.1, 4.0, obdSpeedKph = 0.0, gpsSpeedMps = null)
        filter.accept(40.2, -3.2, 8.0, obdSpeedKph = 0.0, gpsSpeedMps = null)

        val moving = filter.accept(
            latitude = 41.0,
            longitude = -4.0,
            accuracy = 6.0,
            obdSpeedKph = 0.5000001,
            gpsSpeedMps = null,
        )

        assertEquals(41.0, moving.latitude, 1e-9)
        assertEquals(-4.0, moving.longitude, 1e-9)
        assertEquals(6.0, moving.accuracy, 1e-9)
        assertFalse(moving.frozen)
    }

    @Test
    fun `exact boundary 0_5 freezes when anchor exists`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.0, -3.0, 5.0, obdSpeedKph = 10.0, gpsSpeedMps = null)

        val atBoundary = filter.accept(
            latitude = 41.0,
            longitude = -4.0,
            accuracy = 20.0,
            obdSpeedKph = 0.5,
            gpsSpeedMps = null,
        )

        assertEquals(40.0, atBoundary.latitude, 1e-9)
        assertEquals(-3.0, atBoundary.longitude, 1e-9)
        assertEquals(5.0, atBoundary.accuracy, 1e-9)
        assertTrue(atBoundary.frozen)
    }

    @Test
    fun `obd null gps near zero freezes via fallback`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.0, -3.0, 5.0, obdSpeedKph = null, gpsSpeedMps = 5.0)

        val frozen = filter.accept(
            latitude = 40.5,
            longitude = -3.5,
            accuracy = 30.0,
            obdSpeedKph = null,
            gpsSpeedMps = 0.0,
        )

        assertEquals(40.0, frozen.latitude, 1e-9)
        assertEquals(-3.0, frozen.longitude, 1e-9)
        assertEquals(5.0, frozen.accuracy, 1e-9)
        assertTrue(frozen.frozen)
    }

    @Test
    fun `obd null gps moving updates anchor`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.0, -3.0, 5.0, obdSpeedKph = null, gpsSpeedMps = 0.0)

        // 1 m/s = 3.6 kph > 0.5
        val moving = filter.accept(
            latitude = 41.0,
            longitude = -4.0,
            accuracy = 6.0,
            obdSpeedKph = null,
            gpsSpeedMps = 1.0,
        )

        assertEquals(41.0, moving.latitude, 1e-9)
        assertEquals(-4.0, moving.longitude, 1e-9)
        assertEquals(6.0, moving.accuracy, 1e-9)
        assertFalse(moving.frozen)
    }

    @Test
    fun `both speeds null never freezes and accepts current`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.0, -3.0, 5.0, obdSpeedKph = null, gpsSpeedMps = null)

        val next = filter.accept(
            latitude = 41.0,
            longitude = -4.0,
            accuracy = 12.0,
            obdSpeedKph = null,
            gpsSpeedMps = null,
        )

        assertEquals(41.0, next.latitude, 1e-9)
        assertEquals(-4.0, next.longitude, 1e-9)
        assertEquals(12.0, next.accuracy, 1e-9)
        assertFalse(next.frozen)
    }

    @Test
    fun `obd speed preferred over gps when both present`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.0, -3.0, 5.0, obdSpeedKph = 20.0, gpsSpeedMps = 0.0)

        // OBD says stopped; GPS claims moving — OBD wins → freeze
        val frozen = filter.accept(
            latitude = 41.0,
            longitude = -4.0,
            accuracy = 15.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = 10.0,
        )

        assertEquals(40.0, frozen.latitude, 1e-9)
        assertEquals(-3.0, frozen.longitude, 1e-9)
        assertTrue(frozen.frozen)
    }
}
