package com.domivega.gps_car.data.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplePayloadRewriteTest {

    @Test
    fun `replaces tracking_id field value`() {
        val input =
            """{"tracking_id":"local:abc","lat":1.0,"lon":2.0,"acc":3.0,"recorded_at":123}"""
        val out = SamplePayloadRewrite.replaceTrackingId(input, "server-1")
        assertTrue(out.contains(""""tracking_id":"server-1""""))
        assertTrue(!out.contains("local:abc"))
        assertTrue(out.contains(""""lat":1.0"""))
    }

    @Test
    fun `leaves other fields intact when tracking_id already server`() {
        val input = """{"tracking_id":"old-server","recorded_at":1}"""
        val out = SamplePayloadRewrite.replaceTrackingId(input, "new-server")
        assertEquals(
            """{"tracking_id":"new-server","recorded_at":1}""",
            out,
        )
    }
}
