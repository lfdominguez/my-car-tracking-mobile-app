package com.domivega.gps_car.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domivega.gps_car.obd.BluetoothTransport
import com.domivega.gps_car.obd.VehicleObdProfile
import com.domivega.gps_car.ui.ObdDevicePresentation
import com.domivega.gps_car.ui.state.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdDeviceScreen(
    state: SettingsUiState,
    bleDeviceLabel: String = "",
    connectionStatus: String = "",
    protocolOptions: List<Pair<String, String>> = emptyList(),
    transportOptions: List<Pair<String, String>> =
        BluetoothTransport.entries.map { it.name to it.displayName },
    scannedDevices: List<Pair<String, String>> = emptyList(),
    onProtocolSelected: (String) -> Unit = {},
    onTransportSelected: (String) -> Unit = {},
    vehicleObdProfileOptions: List<Pair<String, String>> =
        VehicleObdProfile.entries.map { it.name to it.displayName },
    onVehicleObdProfileSelected: (String) -> Unit = {},
    onVwOdometerDidChange: (String) -> Unit = {},
    onWwhObdOnlyChange: (Boolean) -> Unit = {},
    onObdPerformanceModeChange: (Boolean) -> Unit = {},
    onScanClick: () -> Unit = {},
    onDeviceSelected: (address: String, name: String?) -> Unit = { _, _ -> },
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var protocolExpanded by remember { mutableStateOf(false) }
    var transportExpanded by remember { mutableStateOf(false) }
    var vehicleProfileExpanded by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val selectedProtocolLabel = protocolOptions
        .firstOrNull { it.first == state.obdProtocol }
        ?.second
        ?: state.obdProtocol

    val selectedTransportLabel = transportOptions
        .firstOrNull { it.first == state.bluetoothTransport }
        ?.second
        ?: state.bluetoothTransport

    val selectedVehicleProfileLabel = vehicleObdProfileOptions
        .firstOrNull { it.first == state.vehicleObdProfile }
        ?.second
        ?: state.vehicleObdProfile

    val deviceLabel = ObdDevicePresentation.deviceLabel(
        liveLabel = bleDeviceLabel,
        deviceName = state.bleDeviceName,
        deviceAddress = state.bleDeviceAddress,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(
            title = "Adapter",
            supporting = connectionStatus.ifBlank { "ELM327 transport, device, and protocol" },
        ) {
            if (transportOptions.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = transportExpanded,
                    onExpandedChange = { transportExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedTransportLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Bluetooth transport") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transportExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = transportExpanded,
                        onDismissRequest = { transportExpanded = false },
                    ) {
                        transportOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    transportExpanded = false
                                    onTransportSelected(id)
                                },
                            )
                        }
                    }
                }
            }

            if (state.bluetoothTransport == BluetoothTransport.ClassicSpp.name) {
                Text(
                    text = "Pair in Android Bluetooth settings if needed (PIN often 1234 or 0000). Close Torque/other OBD apps before connecting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Device",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = deviceLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Status: ${connectionStatus.ifBlank { "Unknown" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (protocolOptions.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = protocolExpanded,
                    onExpandedChange = { protocolExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedProtocolLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("OBD Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = protocolExpanded,
                        onDismissRequest = { protocolExpanded = false },
                    ) {
                        protocolOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    protocolExpanded = false
                                    onProtocolSelected(id)
                                },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    showDeviceDialog = true
                    onScanClick()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Scan for BLE devices")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onConnectClick,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    enabled = state.bleDeviceAddress.isNotBlank() && state.obdEnabled,
                ) {
                    Text("Connect")
                }
                OutlinedButton(
                    onClick = onDisconnectClick,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Disconnect")
                }
            }
        }

        SettingsSection(
            title = "Vehicle",
            supporting = "Profile and experimental protocol",
        ) {
            if (vehicleObdProfileOptions.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = vehicleProfileExpanded,
                    onExpandedChange = { vehicleProfileExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedVehicleProfileLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vehicle OBD profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleProfileExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = vehicleProfileExpanded,
                        onDismissRequest = { vehicleProfileExpanded = false },
                    ) {
                        vehicleObdProfileOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vehicleProfileExpanded = false
                                    onVehicleObdProfileSelected(id)
                                },
                            )
                        }
                    }
                }
            }

            if (state.vehicleObdProfile == VehicleObdProfile.VwMqb.name) {
                SettingsTextField(
                    value = state.vwOdometerDid,
                    onValueChange = onVwOdometerDidChange,
                    label = "VW odometer DID (optional hex)",
                )
                Text(
                    text = "4-hex DID override for cluster odometer (e.g. 22A6). Leave empty to try built-in candidates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("WWH-OBD only (experimental)")
                    Text(
                        text = "Engine metrics via UDS 22F4xx only (no classic Mode 01). " +
                            "Applies on next OBD reconnect. May fail if the car has no OBDonUDS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.wwhObdOnly,
                    onCheckedChange = onWwhObdOnlyChange,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Performance mode")
                    Text(
                        text = "Faster ELM polling (expected response lines + aggressive timing). " +
                            "Line suffix applies on the next PID query; ATAT2 on next OBD reconnect. " +
                            "Turn off if PIDs start dropping.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.obdPerformanceMode,
                    onCheckedChange = onObdPerformanceModeChange,
                )
            }
        }
    }

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("BLE devices") },
            text = {
                if (scannedDevices.isEmpty()) {
                    Text("Scanning… no devices found yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                    ) {
                        items(scannedDevices, key = { it.first }) { (address, nameOrBlank) ->
                            val title = nameOrBlank.ifBlank { address }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val name = nameOrBlank.takeIf { it.isNotBlank() }
                                        onDeviceSelected(address, name)
                                        showDeviceDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (nameOrBlank.isNotBlank()) {
                                    Text(
                                        text = address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { onScanClick() }) {
                    Text("Rescan")
                }
            },
        )
    }
}
