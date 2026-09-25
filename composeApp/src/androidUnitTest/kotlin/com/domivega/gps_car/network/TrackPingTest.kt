package com.domivega.gps_car.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPingTest {

    @Test
    fun ping_url_derived_from_start_url_origin() {
        assertEquals(
            "https://track.example.com/api/track/ping",
            pingUrlFromTrackUrl("https://track.example.com/api/track/start"),
        )
        assertEquals(
            "http://10.0.0.5:8080/api/track/ping",
            pingUrlFromTrackUrl("http://10.0.0.5:8080/api/track/start"),
        )
        assertNull(pingUrlFromTrackUrl(""))
        assertNull(pingUrlFromTrackUrl("not-a-url"))
    }

    @Test
    fun explicit_ping_url_wins_and_blank_falls_back_to_start_url() {
        assertEquals(
            "https://other.example.com/api/track/ping",
            TrackPing.resolveUrl(
                pingUrl = " https://other.example.com/api/track/ping ",
                startUrl = "https://track.example.com/api/track/start",
            ),
        )
        assertEquals(
            "https://track.example.com/api/track/ping",
            TrackPing.resolveUrl(pingUrl = "  ", startUrl = "https://track.example.com/api/track/start"),
        )
        assertNull(TrackPing.resolveUrl(pingUrl = "", startUrl = ""))
    }

    @Test
    fun parses_ping_body() {
        val body = """{"ok":true,"car_id":"550e8400-e29b-41d4-a716-446655440000","car_name":"Demo Car","vault_required":false,"extra":1}"""
        val parsed = TrackPing.parseBody(body)!!
        assertTrue(parsed.ok)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", parsed.carId)
        assertEquals("Demo Car", parsed.carName)
        assertEquals(false, parsed.vaultRequired)
        assertNull(TrackPing.parseBody("<html>not json</html>"))
    }

    @Test
    fun status_200_is_ok_with_car_name() {
        val result = TrackPing.classify(
            200,
            """{"ok":true,"car_id":"c1","car_name":"Demo Car","vault_required":false}""",
        )
        assertEquals(ConnectionTestResult.Ok(carName = "Demo Car", vaultRequired = false), result)
    }

    @Test
    fun status_200_surfaces_vault_required() {
        val result = TrackPing.classify(
            200,
            """{"ok":true,"car_id":"c1","car_name":"","vault_required":true}""",
        )
        assertEquals(ConnectionTestResult.Ok(carName = null, vaultRequired = true), result)
    }

    @Test
    fun status_200_with_unexpected_body_fails() {
        assertTrue(TrackPing.classify(200, "<html></html>") is ConnectionTestResult.Failed)
        assertTrue(TrackPing.classify(200, """{"ok":false}""") is ConnectionTestResult.Failed)
    }

    @Test
    fun status_401_and_403_are_unauthorized() {
        assertTrue(TrackPing.classify(401, "") is ConnectionTestResult.Unauthorized)
        assertTrue(TrackPing.classify(403, "revoked") is ConnectionTestResult.Unauthorized)
    }

    @Test
    fun status_404_is_token_not_verified() {
        val result = TrackPing.classify(404, "Not Found")
        assertTrue(result is ConnectionTestResult.TokenNotVerified)
        assertTrue((result as ConnectionTestResult.TokenNotVerified).detail.contains("/api/track/ping"))
    }

    @Test
    fun other_status_fails() {
        val result = TrackPing.classify(500, "boom")
        assertTrue(result is ConnectionTestResult.Failed)
        assertTrue((result as ConnectionTestResult.Failed).detail.contains("500"))
    }
}
