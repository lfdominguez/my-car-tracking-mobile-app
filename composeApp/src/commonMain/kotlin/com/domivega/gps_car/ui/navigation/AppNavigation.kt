package com.domivega.gps_car.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.domivega.gps_car.ui.SettingsViewModel
import com.domivega.gps_car.ui.screens.AboutScreen
import com.domivega.gps_car.ui.screens.DashboardScreen
import com.domivega.gps_car.ui.screens.DebugConsoleScreen
import com.domivega.gps_car.ui.screens.ObdDeviceScreen
import com.domivega.gps_car.ui.screens.SettingsScreen
import com.domivega.gps_car.obd.ObdLogEntry
import com.domivega.gps_car.ui.state.DashboardState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    dashboardState: DashboardState,
    settingsViewModel: SettingsViewModel,
    onObdEnabledChange: (Boolean) -> Unit,
    onRetryUpload: () -> Unit = {},
    onOpenSettings: () -> Unit, // Callback to open legacy/android settings if needed
    onScanQrCode: () -> Unit,
    logoPainter: Painter? = null,
    bleDeviceLabel: String = "",
    connectionStatus: String = "",
    protocolOptions: List<Pair<String, String>> = emptyList(),
    transportOptions: List<Pair<String, String>> = emptyList(),
    scannedDevices: List<Pair<String, String>> = emptyList(),
    onProtocolSelected: (String) -> Unit = {},
    onTransportSelected: (String) -> Unit = {},
    onBleScanClick: () -> Unit = {},
    onBleDeviceSelected: (address: String, name: String?) -> Unit = { _, _ -> },
    onBleConnectClick: () -> Unit = {},
    onBleDisconnectClick: () -> Unit = {},
    obdLogEntries: List<ObdLogEntry> = emptyList(),
    onClearObdLog: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val settingsState by settingsViewModel.uiState.collectAsState()

    // We can use a simple state to switch screens
    var currentScreen by remember { mutableStateOf("Dashboard") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GPS Car Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Digital cockpit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(text = "Dashboard") },
                    selected = currentScreen == "Dashboard",
                    onClick = {
                        currentScreen = "Dashboard"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text(text = "OBD Device") },
                    selected = currentScreen == "OBD Device",
                    onClick = {
                        currentScreen = "OBD Device"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text(text = "Settings") },
                    selected = currentScreen == "Settings",
                    onClick = {
                        currentScreen = "Settings"
                        // For now, we might delegate settings to the Android activity or show a placeholder
                        onOpenSettings() 
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
                 // Logs item removed as requested
                 NavigationDrawerItem(
                    label = { Text(text = "Debug Console") },
                    selected = currentScreen == "Debug",
                    onClick = {
                        currentScreen = "Debug"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) }
                )
                 NavigationDrawerItem(
                    label = { Text(text = "About") },
                    selected = currentScreen == "About",
                    onClick = {
                        currentScreen = "About"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(currentScreen) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                     colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    "Dashboard" -> DashboardScreen(
                        state = dashboardState,
                        onObdEnabledChange = onObdEnabledChange,
                        onRetryUpload = onRetryUpload,
                    )
                    "OBD Device" -> ObdDeviceScreen(
                        state = settingsState,
                        bleDeviceLabel = bleDeviceLabel,
                        connectionStatus = connectionStatus,
                        protocolOptions = protocolOptions,
                        transportOptions = transportOptions,
                        scannedDevices = scannedDevices,
                        onProtocolSelected = onProtocolSelected,
                        onTransportSelected = onTransportSelected,
                        onVehicleObdProfileSelected = settingsViewModel::updateVehicleObdProfile,
                        onVwOdometerDidChange = settingsViewModel::updateVwOdometerDid,
                        onWwhObdOnlyChange = settingsViewModel::updateWwhObdOnly,
                        onScanClick = onBleScanClick,
                        onDeviceSelected = onBleDeviceSelected,
                        onConnectClick = onBleConnectClick,
                        onDisconnectClick = onBleDisconnectClick,
                    )
                    "Settings" -> SettingsScreen(
                        state = settingsState,
                        onApiTokenChange = settingsViewModel::updateApiToken,
                        onStartUrlChange = settingsViewModel::updateStartUrl,
                        onStopUrlChange = settingsViewModel::updateStopUrl,
                        onSampleUrlChange = settingsViewModel::updateSampleUrl,
                        onSamplesUrlChange = settingsViewModel::updateSamplesUrl,
                        onScanQrCode = onScanQrCode,
                        onTestConnection = settingsViewModel::testConnection,
                        onClearQrError = settingsViewModel::clearQrError,
                        onFuelTypeSelected = settingsViewModel::updateFuelType,
                        onFuelStoichAfrChange = settingsViewModel::updateFuelStoichAfr,
                        onFuelDensityGlChange = settingsViewModel::updateFuelDensityGl,
                        onEngineDisplacementLChange = settingsViewModel::updateEngineDisplacementL,
                        onEngineVeChange = settingsViewModel::updateEngineVe,
                        onTankCapacityLChange = settingsViewModel::updateTankCapacityL,
                        onSampleUploadFieldFlagsChange = settingsViewModel::updateSampleUploadFieldFlags,
                    )
                    "Debug" -> DebugConsoleScreen(
                        pidValues = dashboardState.pidValues,
                        pidNames = dashboardState.pidNames,
                        connectionStatus = connectionStatus,
                        logEntries = obdLogEntries,
                        onClearLog = onClearObdLog,
                    )
                    "About" -> AboutScreen(
                        logoPainter = logoPainter
                    )
                    else -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("Screen '$currentScreen' not implemented yet")
                        }
                    }
                }
            }
        }
    }
}
