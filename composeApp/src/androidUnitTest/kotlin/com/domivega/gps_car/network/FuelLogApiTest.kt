package com.domivega.gps_car.network

import com.domivega.gps_car.data.FuelLogEntry
import com.domivega.gps_car.data.TicketReadOutcome
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class FuelLogApiTest {
    @Test
    fun suggestionIsDecodedFromTheServerReply() {
        val body = """
            {"suggestion":{"filled_at_local":"2026-10-09T18:32:00","unit":"L","quantity":42.35,
             "price_per_unit":1.459,"total_cost":61.79,"currency":"EUR","station":"REPSOL"},
             "source":"ai","text":["LITROS 42,35","TOTAL 61,79"]}
        """.trimIndent()
        val out = FuelLogApi.classifyTicket(200, body) as TicketReadOutcome.Suggested
        assertEquals(42.35, out.suggestion.quantity!!, 0.0)
        assertEquals(1.459, out.suggestion.pricePerUnit!!, 0.0)
        assertEquals("2026-10-09T18:32:00", out.suggestion.filledAtLocal)
        assertEquals(listOf("LITROS 42,35", "TOTAL 61,79"), out.text)
        assertTrue(out.refinedByAi)
    }

    @Test
    fun missingFieldsStayNull() {
        val out = FuelLogApi.classifyTicket(200, """{"suggestion":{"unit":"kWh"},"source":"parser","text":[]}""")
            as TicketReadOutcome.Suggested
        assertEquals(null, out.suggestion.quantity)
        assertEquals("kWh", out.suggestion.unit)
    }

    @Test
    fun gatedAccountGetsTheServersMessage() {
        val body = """{"error":"Service not available yet"}"""
        assertEquals(TicketReadOutcome.NotAvailable("Service not available yet"), FuelLogApi.classifyTicket(403, body))
        assertEquals(TicketReadOutcome.NotAvailable("Service not available yet"), FuelLogApi.classifyTicket(503, body))
        assertTrue(FuelLogApi.classifyTicket(404, "") is TicketReadOutcome.NotAvailable)
        assertTrue(FuelLogApi.classifyTicket(401, "") is TicketReadOutcome.Failed)
        assertTrue(FuelLogApi.classifyTicket(200, "not json") is TicketReadOutcome.Failed)
    }

    @Test
    fun localTimeIsSentAsUtc() {
        val madrid = TimeZone.getTimeZone("Europe/Madrid")
        // CEST, UTC+2.
        assertEquals("2026-10-09T16:32:00Z", FuelLogApi.utcFromLocal("2026-10-09 18:32", madrid))
        // CET, UTC+1.
        assertEquals("2026-12-01T07:00:00Z", FuelLogApi.utcFromLocal("2026-12-01 08:00", madrid))
    }

    @Test
    fun requestUsesBackendFieldNamesAndOmitsNulls() {
        val entry = FuelLogEntry(
            filledAtLocal = "2026-10-09 18:32",
            unit = "L",
            quantity = 42.35,
            pricePerUnit = 1.459,
            totalCost = null,
            currency = "EUR",
            station = null,
            odometerKm = 123456.0,
            fullTank = false,
        )
        val json = Json { explicitNulls = false; encodeDefaults = true }
        val body = json.encodeToString(
            FuelLogRequest.serializer(),
            FuelLogApi.request(entry, TimeZone.getTimeZone("UTC")),
        )
        assertEquals(
            """{"filled_at":"2026-10-09T18:32:00Z","unit":"L","quantity":42.35,"price_per_unit":1.459,""" +
                """"currency":"EUR","full_tank":false,"odometer_km":123456.0}""",
            body,
        )
    }

    @Test
    fun phoneCountryPrefersTheNetworkThenSimThenRegion() {
        assertEquals("AR", FuelLogApi.phoneCountry(network = "ar", sim = "uy", locale = "UY"))
        assertEquals("UY", FuelLogApi.phoneCountry(network = "", sim = "uy", locale = "US"))
        assertEquals("UY", FuelLogApi.phoneCountry(network = null, sim = null, locale = "UY"))
        // Emulators and some tablets report junk or nothing at all.
        assertEquals(null, FuelLogApi.phoneCountry(network = "310", sim = " ", locale = ""))
    }

    @Test
    fun endpointsDeriveFromTheTrackUrl() {
        assertEquals(
            "https://cars.example:8443/api/track/fuel-ticket",
            trackEndpointFromTrackUrl("https://cars.example:8443/api/track/start", FuelLogApi.TICKET_PATH),
        )
        assertEquals(null, trackEndpointFromTrackUrl("", FuelLogApi.LOG_PATH))
    }
}
