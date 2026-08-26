package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VwClusterHopPolicyTest {

    @Test
    fun `custom FC retry after plain miss including NO DATA`() {
        // Nivus multi-frame odo often yields plain NO DATA without custom FC —
        // must not require an incomplete multi-frame fragment first.
        assertTrue(VwClusterHopPolicy.shouldRetryWithCustomFlowControl(gotOdometerKm = false))
        assertTrue(
            VwClusterHopPolicy.shouldRetryWithCustomFlowControl(
                gotOdometerKm = false,
                lastRaw = "NO DATA",
            ),
        )
        assertTrue(
            VwClusterHopPolicy.shouldRetryWithCustomFlowControl(
                gotOdometerKm = false,
                lastRaw = null,
            ),
        )
        assertTrue(
            VwClusterHopPolicy.shouldRetryWithCustomFlowControl(
                gotOdometerKm = false,
                lastRaw = "014\r0: 62 22 03 00 03\r>",
            ),
        )
    }

    @Test
    fun `no custom FC retry when plain pass already got km`() {
        assertFalse(VwClusterHopPolicy.shouldRetryWithCustomFlowControl(gotOdometerKm = true))
        assertFalse(
            VwClusterHopPolicy.shouldRetryWithCustomFlowControl(
                gotOdometerKm = true,
                lastRaw = "NO DATA",
            ),
        )
    }

    @Test
    fun `hop uses cluster request header and response filter ids`() {
        assertEquals("714", VwClusterHopPolicy.REQUEST_HEADER_HEX)
        assertEquals("77E", VwClusterHopPolicy.RESPONSE_FILTER_HEX)
        assertTrue(VwClusterHopPolicy.USE_RECEIVE_FILTER)
    }

}
