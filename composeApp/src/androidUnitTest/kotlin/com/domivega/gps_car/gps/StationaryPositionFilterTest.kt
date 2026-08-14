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

        // Small drift while OBD says stopped — stay frozen on anchor
        val frozen = filter.accept(
            latitude = 40.10005,
            longitude = -3.10005,
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
        filter.accept(40.10002, -3.10002, 8.0, obdSpeedKph = 0.0, gpsSpeedMps = null)

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
            latitude = 40.00005,
            longitude = -3.00005,
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
            latitude = 40.00005,
            longitude = -3.00005,
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
    fun `obd zero does not freeze when gps speed says moving`() {
        val filter = StationaryPositionFilter()
        filter.accept(40.0, -3.0, 5.0, obdSpeedKph = 20.0, gpsSpeedMps = 0.0)

        // OBD says stopped; GPS claims moving — max speed wins → unfreeze
        val moving = filter.accept(
            latitude = 41.0,
            longitude = -4.0,
            accuracy = 15.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = 10.0,
        )

        assertEquals(41.0, moving.latitude, 1e-9)
        assertEquals(-4.0, moving.longitude, 1e-9)
        assertEquals(15.0, moving.accuracy, 1e-9)
        assertFalse(moving.frozen)
    }

    @Test
    fun `effectiveSpeedKph uses max of obd and gps`() {
        val filter = StationaryPositionFilter()
        assertEquals(36.0, filter.effectiveSpeedKph(obdSpeedKph = 0.0, gpsSpeedMps = 10.0)!!, 1e-9)
        assertEquals(20.0, filter.effectiveSpeedKph(obdSpeedKph = 20.0, gpsSpeedMps = 1.0)!!, 1e-9)
        assertEquals(0.0, filter.effectiveSpeedKph(obdSpeedKph = 0.0, gpsSpeedMps = 0.0)!!, 1e-9)
    }

    @Test
    fun `small park drift while stopped stays frozen`() {
        val filter = StationaryPositionFilter()
        filter.accept(26.0642303, -80.2473303, 13.0, obdSpeedKph = 0.0, gpsSpeedMps = 0.0)

        // ~8 m north of anchor
        val driftedLat = 26.0642303 + metersToLatDelta(8.0)
        val frozen = filter.accept(
            latitude = driftedLat,
            longitude = -80.2473303,
            accuracy = 13.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = 0.0,
        )

        assertEquals(26.0642303, frozen.latitude, 1e-9)
        assertEquals(-80.2473303, frozen.longitude, 1e-9)
        assertTrue(frozen.frozen)
    }

    @Test
    fun `large jump while speeds say stopped unfreezes and reanchors`() {
        val filter = StationaryPositionFilter()
        filter.accept(26.0642303, -80.2473303, 13.0, obdSpeedKph = 0.0, gpsSpeedMps = 0.0)

        // ~2.4 km away (trip 286710f6 pattern)
        val farLat = 26.085355
        val farLon = -80.251525
        val unfrozen = filter.accept(
            latitude = farLat,
            longitude = farLon,
            accuracy = 4.2,
            obdSpeedKph = 0.0,
            gpsSpeedMps = 0.0,
        )

        assertEquals(farLat, unfrozen.latitude, 1e-9)
        assertEquals(farLon, unfrozen.longitude, 1e-9)
        assertEquals(4.2, unfrozen.accuracy, 1e-9)
        assertFalse(unfrozen.frozen)

        // Next small drift freezes on the new anchor
        val reFrozen = filter.accept(
            latitude = farLat + metersToLatDelta(5.0),
            longitude = farLon,
            accuracy = 10.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = 0.0,
        )
        assertEquals(farLat, reFrozen.latitude, 1e-9)
        assertEquals(farLon, reFrozen.longitude, 1e-9)
        assertTrue(reFrozen.frozen)
    }

    @Test
    fun `jump threshold scales with accuracy max of 50 and acc times 3`() {
        val filter = StationaryPositionFilter()
        // threshold = max(50, 30*3) = 90 m
        filter.accept(40.0, -3.0, 5.0, obdSpeedKph = 0.0, gpsSpeedMps = 0.0)

        val stillUnder = filter.accept(
            latitude = 40.0 + metersToLatDelta(40.0),
            longitude = -3.0,
            accuracy = 30.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = 0.0,
        )
        assertTrue(stillUnder.frozen)
        assertEquals(40.0, stillUnder.latitude, 1e-9)

        val overThreshold = filter.accept(
            latitude = 40.0 + metersToLatDelta(95.0),
            longitude = -3.0,
            accuracy = 30.0,
            obdSpeedKph = 0.0,
            gpsSpeedMps = 0.0,
        )
        assertFalse(overThreshold.frozen)
        assertEquals(40.0 + metersToLatDelta(95.0), overThreshold.latitude, 1e-9)
    }

    @Test
    fun `distanceMeters haversine is sane for short hops`() {
        // ~111.2 km per degree latitude
        val d = StationaryPositionFilter.distanceMeters(0.0, 0.0, 0.001, 0.0)
        assertEquals(111.2, d, 1.0)
    }

    private fun metersToLatDelta(meters: Double): Double =
        meters / 111_320.0
}
