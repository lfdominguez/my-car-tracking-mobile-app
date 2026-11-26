package com.domivega.gps_car.ui.state

import com.domivega.gps_car.ui.UiText
import org.jetbrains.compose.resources.StringResource
import com.domivega.gps_car.generated.resources.Res
import com.domivega.gps_car.generated.resources.msg_refuel_date
import com.domivega.gps_car.generated.resources.msg_refuel_quantity
import com.domivega.gps_car.generated.resources.msg_refuel_not_number
import com.domivega.gps_car.generated.resources.msg_refuel_f_price
import com.domivega.gps_car.generated.resources.msg_refuel_f_total
import com.domivega.gps_car.generated.resources.msg_refuel_f_odometer

import com.domivega.gps_car.data.FuelLogEntry
import com.domivega.gps_car.data.FuelTicketSuggestion

/** Log-refuel form. Fields are kept as typed text so "42," stays editable. */
data class RefuelUiState(
    /** `yyyy-MM-dd HH:mm`, the phone's local time. */
    val filledAt: String = "",
    val unit: String = "L",
    val quantity: String = "",
    val pricePerUnit: String = "",
    val totalCost: String = "",
    val currency: String = "",
    val station: String = "",
    val odometerKm: String = "",
    val fullTank: Boolean = true,
    val reading: Boolean = false,
    val saving: Boolean = false,
    val message: UiText = UiText.Empty,
    val isError: Boolean = false,
    /** Receipt text as the server read it, for checking the suggestion. */
    val receiptText: List<String> = emptyList(),
) {
    val busy: Boolean get() = reading || saving

    /** Fills the fields the receipt answered and leaves the rest as typed. */
    fun withSuggestion(s: FuelTicketSuggestion): RefuelUiState = copy(
        filledAt = s.filledAtLocal?.let(::formLocalTime) ?: filledAt,
        unit = s.unit?.takeIf { it == "L" || it == "kWh" } ?: unit,
        quantity = s.quantity?.let(::formatNumber) ?: quantity,
        pricePerUnit = s.pricePerUnit?.let(::formatNumber) ?: pricePerUnit,
        totalCost = s.totalCost?.let(::formatNumber) ?: totalCost,
        currency = s.currency ?: currency,
        station = s.station ?: station,
    )

    /** Validated entry, or the message to show. */
    fun toEntry(): Result<FuelLogEntry> = runCatching {
        invalidIf(!LOCAL_TIME.matches(filledAt.trim()), UiText.Res(Res.string.msg_refuel_date))
        val q = parseDecimal(quantity)
        invalidIf(q == null || q <= 0, UiText.Res(Res.string.msg_refuel_quantity))
        fun optional(text: String, name: StringResource): Double? {
            if (text.isBlank()) return null
            val v = parseDecimal(text)
            invalidIf(v == null || v < 0, UiText.Res(Res.string.msg_refuel_not_number, listOf(UiText.Res(name))))
            return v
        }
        FuelLogEntry(
            filledAtLocal = filledAt.trim(),
            unit = unit,
            quantity = q!!,
            pricePerUnit = optional(pricePerUnit, Res.string.msg_refuel_f_price),
            totalCost = optional(totalCost, Res.string.msg_refuel_f_total),
            currency = currency.trim().uppercase().ifEmpty { null },
            station = station.trim().ifEmpty { null },
            odometerKm = optional(odometerKm, Res.string.msg_refuel_f_odometer),
            fullTank = fullTank,
        )
    }

    /** Validation failure carrying the localised message to show. */
    class InvalidEntry(val text: UiText) : IllegalArgumentException(text.toString())

    companion object {
        private fun invalidIf(condition: Boolean, text: UiText) {
            if (condition) throw InvalidEntry(text)
        }

        private val LOCAL_TIME = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")

        /** Accepts the decimal comma people type on European keyboards. */
        fun parseDecimal(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

        /** `2026-10-09T18:32:00` → `2026-10-09 18:32`. */
        fun formLocalTime(iso: String): String? =
            iso.replace('T', ' ').take(16).takeIf { LOCAL_TIME.matches(it) }

        fun formatNumber(v: Double): String {
            val asLong = v.toLong()
            return if (v == asLong.toDouble()) asLong.toString() else v.toString()
        }
    }
}
