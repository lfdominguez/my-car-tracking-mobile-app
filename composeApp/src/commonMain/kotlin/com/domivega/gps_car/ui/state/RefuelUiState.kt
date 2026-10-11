package com.domivega.gps_car.ui.state

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
    val message: String = "",
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
        require(LOCAL_TIME.matches(filledAt.trim())) { "Date must look like 2026-10-09 18:32" }
        val q = parseDecimal(quantity)
        require(q != null && q > 0) { "Enter the quantity" }
        fun optional(text: String, name: String): Double? {
            if (text.isBlank()) return null
            val v = parseDecimal(text)
            require(v != null && v >= 0) { "$name is not a number" }
            return v
        }
        FuelLogEntry(
            filledAtLocal = filledAt.trim(),
            unit = unit,
            quantity = q,
            pricePerUnit = optional(pricePerUnit, "Price"),
            totalCost = optional(totalCost, "Total"),
            currency = currency.trim().uppercase().ifEmpty { null },
            station = station.trim().ifEmpty { null },
            odometerKm = optional(odometerKm, "Odometer"),
            fullTank = fullTank,
        )
    }

    companion object {
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
