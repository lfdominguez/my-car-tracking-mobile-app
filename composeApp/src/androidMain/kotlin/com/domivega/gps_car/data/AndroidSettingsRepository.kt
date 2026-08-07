package com.domivega.gps_car.data

import android.content.Context
import com.domivega.gps_car.settings.AppSettings

class AndroidSettingsRepository(context: Context) : SettingsRepository {
    private val appSettings = AppSettings(context)

    override var apiToken: String
        get() = appSettings.apiToken
        set(value) { appSettings.apiToken = value }

    override var startUrl: String
        get() = appSettings.startUrl
        set(value) { appSettings.startUrl = value }

    override var stopUrl: String
        get() = appSettings.stopUrl
        set(value) { appSettings.stopUrl = value }

    override var sampleUrl: String
        get() = appSettings.sampleUrl
        set(value) { appSettings.sampleUrl = value }

    override var samplesUrl: String
        get() = appSettings.samplesUrl
        set(value) { appSettings.samplesUrl = value }

    override var carId: String
        get() = appSettings.carId
        set(value) { appSettings.carId = value }

    override var carName: String
        get() = appSettings.carName
        set(value) { appSettings.carName = value }

    override var bleDeviceAddress: String
        get() = appSettings.bleDeviceAddress
        set(value) { appSettings.bleDeviceAddress = value }

    override var bleDeviceName: String
        get() = appSettings.bleDeviceName
        set(value) { appSettings.bleDeviceName = value }

    override var obdProtocol: String
        get() = appSettings.obdProtocol
        set(value) { appSettings.obdProtocol = value }

    override var vehicleObdProfile: String
        get() = appSettings.vehicleObdProfile
        set(value) { appSettings.vehicleObdProfile = value }

    override var vwOdometerDid: String
        get() = appSettings.vwOdometerDid
        set(value) { appSettings.vwOdometerDid = value }

    override var wwhObdOnly: Boolean
        get() = appSettings.wwhObdOnly
        set(value) { appSettings.wwhObdOnly = value }

    override var fuelType: String
        get() = appSettings.fuelType
        set(value) { appSettings.fuelType = value }

    override var fuelStoichAfr: Double
        get() = appSettings.fuelStoichAfr
        set(value) { appSettings.fuelStoichAfr = value }

    override var fuelDensityGl: Double
        get() = appSettings.fuelDensityGl
        set(value) { appSettings.fuelDensityGl = value }

    override var engineDisplacementL: Double
        get() = appSettings.engineDisplacementL
        set(value) { appSettings.engineDisplacementL = value }

    override var engineVe: Double
        get() = appSettings.engineVe
        set(value) { appSettings.engineVe = value }

    override var tankCapacityL: Double
        get() = appSettings.tankCapacityL
        set(value) { appSettings.tankCapacityL = value }
}
