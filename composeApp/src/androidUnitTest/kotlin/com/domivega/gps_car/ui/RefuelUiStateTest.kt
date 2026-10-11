package com.domivega.gps_car.ui

import com.domivega.gps_car.data.FuelTicketSuggestion
import com.domivega.gps_car.ui.state.RefuelUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelUiStateTest {
    private val form = RefuelUiState(filledAt = "2026-10-10 09:00", odometerKm = "123456")

    @Test
    fun suggestionFillsOnlyWhatTheReceiptSaid() {
        val s = form.copy(station = "Typed").withSuggestion(
            FuelTicketSuggestion(
                filledAtLocal = "2026-10-09T18:32:00",
                quantity = 42.35,
                pricePerUnit = 1.459,
                totalCost = 61.0,
                currency = "EUR",
            ),
        )
        assertEquals("2026-10-09 18:32", s.filledAt)
        assertEquals("42.35", s.quantity)
        assertEquals("1.459", s.pricePerUnit)
        assertEquals("61", s.totalCost)
        assertEquals("Typed", s.station)
        assertEquals("123456", s.odometerKm)
        assertEquals("L", s.unit)
    }

    @Test
    fun unknownUnitFromServerIsIgnored() {
        assertEquals("kWh", form.copy(unit = "kWh").withSuggestion(FuelTicketSuggestion(unit = "gal")).unit)
    }

    @Test
    fun entryAcceptsDecimalCommas() {
        val e = form.copy(quantity = "42,35", pricePerUnit = " 1,459 ", currency = "eur").toEntry().getOrThrow()
        assertEquals(42.35, e.quantity, 0.0)
        assertEquals(1.459, e.pricePerUnit!!, 0.0)
        assertEquals(null, e.totalCost)
        assertEquals("EUR", e.currency)
        assertEquals(123456.0, e.odometerKm!!, 0.0)
    }

    @Test
    fun entryRejectsMissingQuantityBadNumbersAndBadDates() {
        assertTrue(form.toEntry().isFailure)
        assertTrue(form.copy(quantity = "0").toEntry().isFailure)
        assertTrue(form.copy(quantity = "40", totalCost = "abc").toEntry().isFailure)
        assertTrue(form.copy(quantity = "40", filledAt = "9/10/2026").toEntry().isFailure)
    }
}
