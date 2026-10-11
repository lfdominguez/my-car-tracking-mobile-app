package com.domivega.gps_car.data

import com.domivega.gps_car.network.ApiClient

class ApiFuelLogBackend(private val api: ApiClient) : FuelLogBackend {
    override suspend fun readTicket(photoJpeg: ByteArray): TicketReadOutcome = api.readFuelTicket(photoJpeg)
    override suspend fun save(entry: FuelLogEntry): Result<Unit> = api.saveFuelLog(entry)
}
