package com.domivega.gps_car.gps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsFixFreshnessTest {

    private fun usable(
        fixAgeMs: Long?,
        accuracyMeters: Double?,
        maxAccuracyMeters: Double = 15.0,
    ) = GpsFixFreshness.isUsable(fixAgeMs, accuracyMeters, maxAccuracyMeters)

    @Test
    fun `a recent accurate fix is usable`() {
        assertTrue(usable(fixAgeMs = 400L, accuracyMeters = 6.0))
        assertTrue(usable(fixAgeMs = 0L, accuracyMeters = 15.0))
    }

    @Test
    fun `no fix at all is not usable`() {
        assertFalse(usable(fixAgeMs = null, accuracyMeters = 6.0))
        assertFalse(usable(fixAgeMs = 400L, accuracyMeters = null))
    }

    /** Entering a tunnel: the last fix keeps aging until it stops describing "here". */
    @Test
    fun `a stale fix is not usable`() {
        assertTrue(usable(fixAgeMs = GpsFixFreshness.DEFAULT_MAX_FIX_AGE_MS, accuracyMeters = 6.0))
        assertFalse(
            usable(fixAgeMs = GpsFixFreshness.DEFAULT_MAX_FIX_AGE_MS + 1L, accuracyMeters = 6.0),
        )
        assertFalse(usable(fixAgeMs = 60_000L, accuracyMeters = 3.0))
    }

    @Test
    fun `a coarse fix is not usable`() {
        assertFalse(usable(fixAgeMs = 200L, accuracyMeters = 15.1))
        assertFalse(usable(fixAgeMs = 200L, accuracyMeters = 400.0))
    }

    /** A fix from the future means the monotonic clocks disagree — do not trust it. */
    @Test
    fun `nonsense ages and accuracies are not usable`() {
        assertFalse(usable(fixAgeMs = -1L, accuracyMeters = 5.0))
        assertFalse(usable(fixAgeMs = 200L, accuracyMeters = -1.0))
        assertFalse(usable(fixAgeMs = 200L, accuracyMeters = Double.NaN))
        assertFalse(usable(fixAgeMs = 200L, accuracyMeters = Double.POSITIVE_INFINITY))
    }
}
