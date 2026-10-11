package com.domivega.gps_car.network

import com.domivega.gps_car.data.FuelLogEntry
import com.domivega.gps_car.data.FuelTicketSuggestion
import com.domivega.gps_car.data.TicketReadOutcome
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Device-token fuel log: `POST /api/track/fuel-ticket` (photo) and `/api/track/fuel-log`. */
object FuelLogApi {
    const val TICKET_PATH = "/api/track/fuel-ticket"
    const val LOG_PATH = "/api/track/fuel-log"

    private val json = Json { ignoreUnknownKeys = true }

    /** Maps the fuel-ticket reply. 403 and 503 carry the server's own message. */
    fun classifyTicket(code: Int, body: String): TicketReadOutcome = when (code) {
        in 200..299 -> runCatching {
            val r = json.decodeFromString(FuelTicketResponse.serializer(), body)
            TicketReadOutcome.Suggested(r.suggestion.toSuggestion(), r.text, r.source == "ai")
        }.getOrElse { TicketReadOutcome.Failed("unexpected reply") }
        403, 503 -> TicketReadOutcome.NotAvailable(errorMessage(body) ?: "Service not available yet")
        401 -> TicketReadOutcome.Failed("device token rejected")
        404 -> TicketReadOutcome.NotAvailable("The server does not support receipt reading")
        413 -> TicketReadOutcome.Failed("photo too large")
        else -> TicketReadOutcome.Failed(errorMessage(body) ?: "HTTP $code")
    }

    private fun errorMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun request(entry: FuelLogEntry, zone: TimeZone = TimeZone.getDefault()): FuelLogRequest =
        FuelLogRequest(
            filledAt = utcFromLocal(entry.filledAtLocal, zone),
            unit = entry.unit,
            quantity = entry.quantity,
            pricePerUnit = entry.pricePerUnit,
            totalCost = entry.totalCost,
            currency = entry.currency,
            fullTank = entry.fullTank,
            station = entry.station,
            odometerKm = entry.odometerKm,
        )

    /** `yyyy-MM-dd HH:mm` in [zone] → RFC3339 UTC. java.time needs API 26; minSdk is 24. */
    fun utcFromLocal(local: String, zone: TimeZone): String {
        val parse = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = zone
            isLenient = false
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parse.parse(local) ?: error("bad date: $local")
        return format.format(date)
    }

    /**
     * Where the phone is: the mobile network's country (right even when roaming),
     * else the SIM's, else the phone's region setting. Alpha-2, upper case.
     */
    fun phoneCountry(network: String?, sim: String?, locale: String?): String? =
        listOf(network, sim, locale)
            .map { it?.trim().orEmpty() }
            .firstOrNull { it.length == 2 && it.all(Char::isLetter) }
            ?.uppercase(Locale.US)

    fun nowLocal(zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }
            .format(java.util.Date())
}

@Serializable
data class FuelTicketResponse(
    val suggestion: FuelTicketSuggestionDto = FuelTicketSuggestionDto(),
    val source: String = "parser",
    val text: List<String> = emptyList(),
)

@Serializable
data class FuelTicketSuggestionDto(
    @SerialName("filled_at_local") val filledAtLocal: String? = null,
    val unit: String? = null,
    val quantity: Double? = null,
    @SerialName("price_per_unit") val pricePerUnit: Double? = null,
    @SerialName("total_cost") val totalCost: Double? = null,
    val currency: String? = null,
    val station: String? = null,
) {
    fun toSuggestion() = FuelTicketSuggestion(
        filledAtLocal = filledAtLocal,
        unit = unit,
        quantity = quantity,
        pricePerUnit = pricePerUnit,
        totalCost = totalCost,
        currency = currency,
        station = station,
    )
}

/** Body of `POST /api/track/fuel-log`, the backend's `FuelRequest`. */
@Serializable
data class FuelLogRequest(
    @SerialName("filled_at") val filledAt: String,
    val unit: String,
    val quantity: Double,
    @SerialName("price_per_unit") val pricePerUnit: Double? = null,
    @SerialName("total_cost") val totalCost: Double? = null,
    val currency: String? = null,
    @SerialName("full_tank") val fullTank: Boolean = true,
    val station: String? = null,
    @SerialName("odometer_km") val odometerKm: Double? = null,
)
