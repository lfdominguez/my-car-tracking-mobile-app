package com.domivega.gps_car

import android.Manifest
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.domivega.gps_car.data.AndroidCarMetricSource
import com.domivega.gps_car.data.AndroidSettingsRepository
import com.domivega.gps_car.data.ApiBackendConnectionTester
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.settings.AppSettings
import com.domivega.gps_car.models.LocationViewModel
import com.domivega.gps_car.objects.GpsDataSource
import com.domivega.gps_car.obd.BluetoothTransport
import com.domivega.gps_car.obd.ObdBleManager
import com.domivega.gps_car.obd.ObdPresenceController
import com.domivega.gps_car.obd.ObdProtocol
import com.domivega.gps_car.ui.MainViewModel
import com.domivega.gps_car.ui.SettingsViewModel
import com.domivega.gps_car.ui.navigation.AppNavigation
import com.domivega.gps_car.ui.theme.AutomotiveTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions


/**
 * Portrait, QR-only scan options for platform provisioning codes.
 *
 * ZXing's default CaptureActivity is sensorLandscape; unlocking orientation
 * forces a landscape barcode UI and makes dense JSON QRs hard to scan.
 */
private fun provisioningQrScanOptions(): ScanOptions =
    ScanOptions()
        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt("Scan the device QR from the web app")
        .setOrientationLocked(true)
        .setBeepEnabled(false)
        .setBarcodeImageEnabled(false)

@Composable
@Preview
fun App() {
    AutomotiveTheme {
        val context = LocalContext.current
        
        // --- Infrastructure: Service & Tracking State ---
        // Keys must match ForegroundTrackingService's prefs
        val prefsName = remember { "tracking_prefs" }
        val keyTrackingId = remember { "tracking_id" }
        
        var isTracking by remember { mutableStateOf(false) }

        // Setup SharedPreferences listener
        DisposableEffect(Unit) {
            val prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
            
            // Initial check
            isTracking = prefs.getString(keyTrackingId, null) != null

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == keyTrackingId) {
                    isTracking = prefs.getString(keyTrackingId, null) != null
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        // --- ViewModel & Data ---
        // We inject the Android-specific CarMetricSource here
        // Using remember for simplicity (Note: in production, use a ViewModelProvider/Factory for rotation support)
        val viewModel = remember {
             MainViewModel(
                 carMetricSource = AndroidCarMetricSource()
                 // GpsDataSource is default
             )
        }

        val settingsViewModel = remember {
            val appSettings = AppSettings(context)
            SettingsViewModel(
                repository = AndroidSettingsRepository(context),
                connectionTester = ApiBackendConnectionTester(ApiClient(appSettings)),
            )
        }
        
        // Sync tracking state to ViewModel
        LaunchedEffect(isTracking) {
            viewModel.updateTrackingState(isTracking)
        }

        val dashboardState by viewModel.uiState.collectAsState()
        val settingsState by settingsViewModel.uiState.collectAsState()

        val locationViewModel = remember {
            LocationViewModel(GpsDataSource)
        }

        // --- BLE OBD status / scan results ---
        val connectionStatus by ObdBleManager.connectionStatus.collectAsState()
        val obdLogEntries by ObdBleManager.debugLog.collectAsState()
        val scannedBleDevices by ObdBleManager.scannedDevices.collectAsState()
        val protocolOptions = remember {
            ObdProtocol.entries.map { it.name to it.displayName }
        }
        val transportOptions = remember {
            BluetoothTransport.entries.map { it.name to it.displayName }
        }
        val scannedDevicePairs = remember(scannedBleDevices) {
            scannedBleDevices.map { device ->
                device.address to (device.name?.takeIf { it.isNotBlank() } ?: "")
            }
        }
        val bleDeviceLabel = remember(settingsState.bleDeviceName, settingsState.bleDeviceAddress) {
            when {
                settingsState.bleDeviceName.isNotBlank() && settingsState.bleDeviceAddress.isNotBlank() ->
                    "${settingsState.bleDeviceName} (${settingsState.bleDeviceAddress})"
                settingsState.bleDeviceAddress.isNotBlank() -> settingsState.bleDeviceAddress
                settingsState.bleDeviceName.isNotBlank() -> settingsState.bleDeviceName
                else -> ""
            }
        }

        // --- QR Scanner ---
        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result.contents
            if (contents.isNullOrBlank()) {
                // User backed out or decoder returned empty — don't toast success.
                return@rememberLauncherForActivityResult
            }
            settingsViewModel.updateSettingsFromQr(contents)
            val err = settingsViewModel.uiState.value.qrError
            val msg = if (err.isNotBlank()) err else "Settings updated from QR"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                scanLauncher.launch(provisioningQrScanOptions())
            } else {
                Toast.makeText(context, "Camera permission required for scanning", Toast.LENGTH_SHORT).show()
            }
        }

        var pendingBleAction by remember { mutableStateOf<String?>(null) }

        val blePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val granted = results.values.all { it }
            val action = pendingBleAction
            pendingBleAction = null
            if (granted) {
                when (action) {
                    "connect" -> ObdBleManager.connect()
                    else -> ObdBleManager.startScan()
                }
            } else {
                Toast.makeText(
                    context,
                    "Bluetooth permissions are required for OBD adapters",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        fun blePermissionsNeeded(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                // Pre-Android 12 BLE scan requires location; classic BLUETOOTH is install-time.
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        fun hasBlePermissions(): Boolean {
            return blePermissionsNeeded().all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun startBleScanWithPermissions() {
            if (hasBlePermissions()) {
                ObdBleManager.startScan()
            } else {
                pendingBleAction = "scan"
                blePermissionLauncher.launch(blePermissionsNeeded())
            }
        }

        fun connectWithPermissions() {
            if (hasBlePermissions()) {
                ObdBleManager.connect()
            } else {
                pendingBleAction = "connect"
                blePermissionLauncher.launch(blePermissionsNeeded())
            }
        }

        // --- UI ---
        AppNavigation(
            dashboardState = dashboardState,
            settingsViewModel = settingsViewModel,
            locationViewModel = locationViewModel,
            onToggleTracking = {
                if (isTracking) {
                     context.startForegroundServiceCompat(ForegroundTrackingService.ACTION_STOP)
                } else {
                     context.startForegroundServiceCompat(ForegroundTrackingService.ACTION_START)
                }
            },
            onOpenSettings = {
                // Placeholder for settings navigation
            },
            onScanQrCode = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    scanLauncher.launch(provisioningQrScanOptions())
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            logoPainter = painterResource(R.drawable.ic_app_logo),
            bleDeviceLabel = bleDeviceLabel,
            connectionStatus = connectionStatus,
            protocolOptions = protocolOptions,
            transportOptions = transportOptions,
            scannedDevices = scannedDevicePairs,
            onProtocolSelected = { protocolName ->
                val protocol = runCatching { ObdProtocol.valueOf(protocolName) }
                    .getOrDefault(ObdProtocol.DEFAULT)
                ObdBleManager.setProtocol(protocol)
                settingsViewModel.updateObdProtocol(protocol.name)
            },
            onTransportSelected = { transportName ->
                val t = BluetoothTransport.fromName(transportName)
                settingsViewModel.updateBluetoothTransport(t.name)
                ObdBleManager.setBluetoothTransport(t)
            },
            onBleScanClick = { startBleScanWithPermissions() },
            onBleDeviceSelected = { address, name ->
                ObdBleManager.selectDevice(address, name)
                settingsViewModel.updateBleDevice(address, name.orEmpty())
                (context as? android.app.Activity)?.let { activity ->
                    ObdPresenceController.requestAssociationIfNeeded(activity, address)
                }
                connectWithPermissions()
            },
            onBleConnectClick = { connectWithPermissions() },
            onBleDisconnectClick = { ObdBleManager.disconnect() },
            obdLogEntries = obdLogEntries,
            onClearObdLog = { ObdBleManager.clearDebugLog() },
        )
    }
}
