package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdometerReadingTest {

    @Test
    fun `prefers A6 vehicle odometer over PID 31`() {
        val km = OdometerReading.fromPidValues(
            mapOf(
                "a6" to 20123.4,
                "31" to 5700.0,
            ),
        )
        assertEquals(20123.4, km!!, 0.001)
    }

    @Test
    fun `does not use PID 31 as odometer fallback`() {
        assertNull(OdometerReading.fromPidValues(mapOf("31" to 5700.0)))
    }

    @Test
    fun `returns null when odometer unavailable`() {
        assertNull(OdometerReading.fromPidValues(mapOf("0d" to 50.0)))
    }
}
