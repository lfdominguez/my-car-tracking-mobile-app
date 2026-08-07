package com.domivega.gps_car.network

import android.util.Log
import com.domivega.gps_car.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient(private val settings: AppSettings) {
    private val json = Json {
        ignoreUnknownKeys = true
        // Do not include fields that are null in JSON (so optional car fields are omitted when unavailable)
        explicitNulls = false
        encodeDefaults = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun postJson(url: String, body: String): Result<String> {
        // Use withContext(Dispatchers.IO) to ensure the synchronous network call
        // runs on a background I/O thread, avoiding NetworkOnMainThreadException.
        return withContext(Dispatchers.IO) {
            runCatching {
                val requestBody = body.toRequestBody(JSON_MEDIA_TYPE)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    // Use a standard HTTP Content-Type header explicitly for clarity
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Basic ${settings.apiToken}")
                    .build()

                Log.d("ApiClient", "Sending POST to $url. Body size: ${requestBody.contentLength()} bytes")

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        // Include the response body in the error for better debugging
                        val errorBody = resp.body.string()
                        error("HTTP ${resp.code}: $errorBody")
                    }
                    // Check for null body before calling string()
                    resp.body.string()
                }
            }
        }
    }

    suspend fun startSession(): Result<StartResponse> {
        val startedAtMs = System.currentTimeMillis()
        val tank = settings.tankCapacityL.takeIf { it > 0.0 }
        val startRequest = StartRequest(
            timestampStart = rfc3339FromEpochMillis(startedAtMs),
            tankCapacityL = tank,
        )

        Log.d("ApiClient", "Starting session at $startedAtMs (${startRequest.timestampStart})")

        val body = json.encodeToString(StartRequest.serializer(), startRequest)
        val r = postJson(settings.startUrl, body)
        return r.map { responseBody ->
            StartResponse(id = resolveTrackingId(responseBody, startedAtMs))
        }
    }

    suspend fun getUrl(url: String, authorized: Boolean = false): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(url).get()
                if (authorized) {
                    builder.addHeader("Authorization", "Basic ${settings.apiToken}")
                }
                client.newCall(builder.build()).execute().use { resp ->
                    val responseBody = resp.body.string()
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: $responseBody")
                    }
                    responseBody
                }
            }
        }
    }

    /**
     * Health check on the API origin, then start/stop smoke with the device token.
     * Creates a short finished track when the token is valid.
     */
    suspend fun testConnection(): ConnectionTestResult {
        val healthUrl = healthUrlFromTrackUrl(settings.startUrl)
            ?: return ConnectionTestResult.Failed("Start URL is missing or invalid")

        val health = getUrl(healthUrl, authorized = false)
        if (health.isFailure) {
            return ConnectionTestResult.Unreachable(
                health.exceptionOrNull()?.message ?: "health failed",
            )
        }

        if (settings.apiToken.isBlank()) {
            return ConnectionTestResult.Failed("API token is empty")
        }

        val start = startSession()
        if (start.isFailure) {
            val msg = start.exceptionOrNull()?.message.orEmpty()
            return if (msg.contains("HTTP 401") || msg.contains("HTTP 403")) {
                ConnectionTestResult.Unauthorized(msg)
            } else {
                ConnectionTestResult.Failed(msg.ifBlank { "start failed" })
            }
        }

        val id = start.getOrNull()?.id
        if (!id.isNullOrBlank()) {
            stopSession(id)
        }
        return ConnectionTestResult.Ok
    }

    suspend fun stopSession(id: String): Result<Unit> {
        val body = json.encodeToString(StopRequest.serializer(), StopRequest(id))
        return postJson(settings.stopUrl, body).map { }
    }

    suspend fun sendSample(sample: Sample): Result<Unit> {
        val body = json.encodeToString(Sample.serializer(), sample)
        return postJson(settings.sampleUrl, body).map { }
    }

    suspend fun sendSamples(samples: List<Sample>): Result<SampleBatchResponse> {
        val request = SampleBatchRequest(samples)
        val body = json.encodeToString(SampleBatchRequest.serializer(), request)
        return postJson(settings.samplesUrl, body).map {
            json.decodeFromString(SampleBatchResponse.serializer(), it)
        }
    }
}

sealed class ConnectionTestResult {
    data object Ok : ConnectionTestResult()
    data class Unreachable(val detail: String) : ConnectionTestResult()
    data class Unauthorized(val detail: String) : ConnectionTestResult()
    data class Failed(val detail: String) : ConnectionTestResult()
}

@Serializable
data class StartRequest(
    /** RFC3339 / ISO-8601 instant string (Rust DateTime). */
    @SerialName("timestamp_start")
    val timestampStart: String,
    /** Optional tank capacity (L) for fuel-level trip cross-check; omit when unknown. */
    @SerialName("tank_capacity_l")
    val tankCapacityL: Double? = null,
)


@Serializable
data class StartResponse(
    @SerialName("id") val id: String
)

@Serializable
data class StopRequest(
    val id: String
)

@Serializable
data class SampleBatchRequest(
    val samples: List<Sample>
)

@Serializable
data class SampleBatchResponse(
    val accepted: Int,
    val rejected: List<RejectedSample>
)

@Serializable
data class RejectedSample(
    @SerialName("recorded_at")
    val recordedAt: Long,
    val reason: String
)

@Serializable
data class Sample(
    @SerialName("tracking_id")
    val trackingId: String,

    @SerialName("recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),

    val lat: Double,
    val lon: Double,
    val acc: Double,

    // Fuel & Engine Performance
    @SerialName("vehicle_engine_rpm")
    val vehicleEngineRpm: Double? = 0.0,
    @SerialName("vehicle_speed_kph")
    val vehicleSpeedKph: Double? = 0.0,
    @SerialName("fuel_consumption_rate")
    val fuelConsumptionRate: Double? = null,
    @SerialName("engine_load_pct")
    val engineLoadPct: Double? = null,
    @SerialName("absolute_engine_load_pct")
    val absoluteEngineLoadPct: Double? = null,
    @SerialName("short_term_fuel_trim_pct")
    val shortTermFuelTrimPct: Double? = null,
    @SerialName("long_term_fuel_trim_pct")
    val longTermFuelTrimPct: Double? = null,
    @SerialName("fuel_level_pct")
    val fuelLevelPct: Double? = null,

    // Driving Style & Safety
    @SerialName("accelerator_pedal_pct")
    val acceleratorPedalPct: Double? = null,
    @SerialName("ambient_air_temp_c")
    val ambientAirTempC: Double? = null,

    // Vehicle Health & Context
    @SerialName("odometer_value_km")
    val odometerValueKm: Double? = null,
    @SerialName("engine_coolant_temp_c")
    val engineCoolantTempC: Double? = null,
    @SerialName("manifold_absolute_pressure_kpa")
    val manifoldAbsolutePressureKpa: Double? = null,
    @SerialName("control_module_voltage")
    val controlModuleVoltage: Double? = null,
    @SerialName("engine_on_time")
    val engineOnTime: Double? = null,
    @SerialName(value="mass_air_flow")
    val massAirFlow: Double? = null,
    @SerialName(value="lambda_cmd")
    val lambdaCmd: Double? = null,
    /** OBD PID 33 barometric pressure (kPa). */
    @SerialName("atmospheric_pressure")
    val atmosphericPressure: Double? = null,
    /** OBD PID 0F intake air temperature (°C). */
    @SerialName("intake_air_temperature")
    val intakeAirTemperature: Double? = null,
)
