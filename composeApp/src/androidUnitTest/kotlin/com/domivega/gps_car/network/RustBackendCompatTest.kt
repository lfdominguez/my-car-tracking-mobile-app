package com.domivega.gps_car.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RustBackendCompatTest {
    @Test
    fun rfc3339_from_millis_is_iso_instant() {
        assertEquals("2024-01-02T03:04:05.123Z", rfc3339FromEpochMillis(1704164645123L))
    }

    @Test
    fun tracking_id_prefers_server_id() {
        assertEquals("server-id", resolveTrackingId(serverBody = """{"id":"server-id"}""", startedAtMs = 1L))
        assertEquals(
            "server-id",
            resolveTrackingId(serverBody = """{"message":"ok","id":"server-id"}""", startedAtMs = 1L),
        )
    }

    @Test
    fun tracking_id_falls_back_to_millis_string_on_empty_or_invalid_body() {
        assertEquals("1704164645123", resolveTrackingId(serverBody = "", startedAtMs = 1704164645123L))
        assertEquals("1704164645123", resolveTrackingId(serverBody = "   ", startedAtMs = 1704164645123L))
        assertEquals("1704164645123", resolveTrackingId(serverBody = "{}", startedAtMs = 1704164645123L))
        assertEquals("1704164645123", resolveTrackingId(serverBody = "not-json", startedAtMs = 1704164645123L))
    }

    @Test
    fun health_url_from_start_url() {
        assertEquals(
            "https://track.example.com/health",
            healthUrlFromTrackUrl("https://track.example.com/api/track/start"),
        )
        assertEquals(
            "http://10.0.0.5:8080/health",
            healthUrlFromTrackUrl("http://10.0.0.5:8080/api/track/start"),
        )
        assertNull(healthUrlFromTrackUrl(""))
        assertNull(healthUrlFromTrackUrl("not-a-url"))
    }
}
