package com.domivega.gps_car.data

import android.content.Context
import android.telephony.TelephonyManager
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.network.FuelLogApi
import java.util.Locale

class ApiFuelLogBackend(
    private val api: ApiClient,
    private val context: Context,
) : FuelLogBackend {
    override suspend fun readTicket(photoJpeg: ByteArray): TicketReadOutcome =
        api.readFuelTicket(photoJpeg, phoneCountry())

    override suspend fun save(entry: FuelLogEntry): Result<Unit> = api.saveFuelLog(entry)

    /** Needs no permission: country ISO codes are not location data on Android. */
    private fun phoneCountry(): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return FuelLogApi.phoneCountry(
            network = runCatching { tm?.networkCountryIso }.getOrNull(),
            sim = runCatching { tm?.simCountryIso }.getOrNull(),
            locale = Locale.getDefault().country,
        )
    }
}
