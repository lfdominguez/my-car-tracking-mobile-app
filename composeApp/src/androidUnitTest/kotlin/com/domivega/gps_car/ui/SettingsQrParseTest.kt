package com.domivega.gps_car.ui

import com.domivega.gps_car.data.SettingsRepository
import com.domivega.gps_car.settings.SampleUploadFieldFlags
import com.domivega.gps_car.ui.state.SettingsUiState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory settings store for QR apply tests (no Android SharedPreferences).
 */
private class FakeSettingsRepository : SettingsRepository {
    override var apiToken: String = ""
    override var startUrl: String = ""
    override var stopUrl: String = ""
    override var sampleUrl: String = ""
    override var samplesUrl: String = ""
    override var carId: String = ""
    override var carName: String = ""
    override var bleDeviceAddress: String = ""
    override var bleDeviceName: String = ""
    override var bluetoothTransport: String = "Ble"
    override var obdProtocol: String = "ISO_15765_4_CAN_11_500"
    override var vehicleObdProfile: String = "Generic"
    override var vwOdometerDid: String = ""
    override var wwhObdOnly: Boolean = false
    override var obdEnabled: Boolean = true
    override var fuelType: String = "E10"
    override var fuelStoichAfr: Double = 14.08
    override var fuelDensityGl: Double = 745.0
    override var engineDisplacementL: Double = 1.0
    override var engineVe: Double = 0.85
    override var tankCapacityL: Double = 0.0
    override var sampleUploadFieldFlags: SampleUploadFieldFlags = SampleUploadFieldFlags.ALL_ENABLED
}

class SettingsQrParseTest {

    private val platformQr = """
        {
          "apiToken": "deadbeefcafebabe0123456789abcdef0123456789abcdef0123456789abcdef",
          "startUrl": "https://track.example.com/api/track/start",
          "stopUrl": "https://track.example.com/api/track/stop",
          "sampleUrl": "https://track.example.com/api/track/sample",
          "samplesUrl": "https://track.example.com/api/track/samples",
          "fuelType": "E10",
          "fuelStoichAfr": 14.08,
          "fuelDensityGl": 745.0,
          "engineDisplacementL": 1.0,
          "engineVe": 0.85,
          "carId": "550e8400-e29b-41d4-a716-446655440000",
          "carName": "Demo Car"
        }
    """.trimIndent()

    @Test
    fun platformProvisioningJson_appliesTokenUrlsAndCar() {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)

        vm.updateSettingsFromQr(platformQr)

        assertEquals("", vm.uiState.value.qrError)
        assertEquals(
            "deadbeefcafebabe0123456789abcdef0123456789abcdef0123456789abcdef",
            repo.apiToken,
        )
        assertEquals("https://track.example.com/api/track/start", repo.startUrl)
        assertEquals("https://track.example.com/api/track/samples", repo.samplesUrl)
        assertEquals("Demo Car", repo.carName)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", repo.carId)
        assertEquals(14.08, repo.fuelStoichAfr, 0.0001)
    }

    @Test
    fun invalidQr_setsUserVisibleError() {
        val vm = SettingsViewModel(FakeSettingsRepository())
        vm.updateSettingsFromQr("not-json")
        assertTrue(vm.uiState.value.qrError.contains("Couldn't read QR settings"))
    }

    @Test
    fun settingsUiState_roundTrip_matchesPlatformKeys() {
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString(SettingsUiState.serializer(), platformQr)
        assertEquals("Demo Car", decoded.carName)
        assertTrue(decoded.apiToken.length == 64)
        assertEquals("Generic", decoded.vehicleObdProfile)
        assertEquals("", decoded.vwOdometerDid)
    }

    @Test
    fun qrWithVehicleProfile_appliesProfileAndDid() {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        val qr = """
            {
              "apiToken": "tok",
              "startUrl": "https://track.example.com/api/track/start",
              "stopUrl": "https://track.example.com/api/track/stop",
              "sampleUrl": "https://track.example.com/api/track/sample",
              "samplesUrl": "https://track.example.com/api/track/samples",
              "vehicleObdProfile": "VwMqb",
              "vwOdometerDid": "22a6"
            }
        """.trimIndent()

        vm.updateSettingsFromQr(qr)

        assertEquals("", vm.uiState.value.qrError)
        assertEquals("VwMqb", repo.vehicleObdProfile)
        assertEquals("22A6", repo.vwOdometerDid)
    }

    @Test
    fun updateVehicleObdProfile_normalizesUnknownToGeneric() {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.updateVehicleObdProfile("not-a-profile")
        assertEquals("Generic", repo.vehicleObdProfile)
        assertEquals("Generic", vm.uiState.value.vehicleObdProfile)
    }

    @Test
    fun updateVwOdometerDid_stripsNonAlnumAndUppercases() {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.updateVwOdometerDid(" 22-a6 ")
        assertEquals("22A6", repo.vwOdometerDid)
        assertEquals("22A6", vm.uiState.value.vwOdometerDid)
    }

    @Test
    fun updateWwhObdOnly_persistsFlag() {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        assertEquals(false, vm.uiState.value.wwhObdOnly)
        vm.updateWwhObdOnly(true)
        assertEquals(true, repo.wwhObdOnly)
        assertEquals(true, vm.uiState.value.wwhObdOnly)
    }

    @Test
    fun updateObdEnabled_persistsFlag() {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        assertEquals(true, vm.uiState.value.obdEnabled)
        vm.updateObdEnabled(false)
        assertEquals(false, repo.obdEnabled)
        assertEquals(false, vm.uiState.value.obdEnabled)
    }
}
