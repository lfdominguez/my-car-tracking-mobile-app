package com.domivega.gps_car.data

interface SettingsRepository {
    var apiToken: String
    var startUrl: String
    var stopUrl: String
    var sampleUrl: String
    var samplesUrl: String
    var carId: String
    var carName: String
    var bleDeviceAddress: String
    var bleDeviceName: String
    var obdProtocol: String
    var vehicleObdProfile: String
    var vwOdometerDid: String
    var fuelType: String
    var fuelStoichAfr: Double
    var fuelDensityGl: Double
    var engineDisplacementL: Double
    var engineVe: Double
}
