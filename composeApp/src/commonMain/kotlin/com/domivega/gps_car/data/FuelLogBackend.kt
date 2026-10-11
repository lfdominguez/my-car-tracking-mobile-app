package com.domivega.gps_car.data

/**
 * Fuel log on the platform (implemented on Android over the device token):
 * read a receipt photo into a suggestion, and store a confirmed entry.
 */
interface FuelLogBackend {
    suspend fun readTicket(photoJpeg: ByteArray): TicketReadOutcome
    suspend fun save(entry: FuelLogEntry): Result<Unit>
}

/** What the server read off a receipt. Every value may be missing. */
data class FuelTicketSuggestion(
    /** Receipt wall-clock time, `yyyy-MM-ddTHH:mm[:ss]`, no zone. */
    val filledAtLocal: String? = null,
    /** `L` or `kWh`. */
    val unit: String? = null,
    val quantity: Double? = null,
    val pricePerUnit: Double? = null,
    val totalCost: Double? = null,
    val currency: String? = null,
    val station: String? = null,
)

sealed class TicketReadOutcome {
    data class Suggested(
        val suggestion: FuelTicketSuggestion,
        /** The receipt as read, row by row. */
        val text: List<String>,
        val refinedByAi: Boolean,
    ) : TicketReadOutcome()

    /** OCR is off for this account or server; manual entry still works. */
    data class NotAvailable(val message: String) : TicketReadOutcome()
    data class Failed(val detail: String) : TicketReadOutcome()
}

/** A confirmed entry; [filledAtLocal] is `yyyy-MM-dd HH:mm` in the phone's zone. */
data class FuelLogEntry(
    val filledAtLocal: String,
    val unit: String,
    val quantity: Double,
    val pricePerUnit: Double?,
    val totalCost: Double?,
    val currency: String?,
    val station: String?,
    val odometerKm: Double?,
    val fullTank: Boolean,
)
