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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domivega.gps_car.fuel.FuelTypePreset
import com.domivega.gps_car.obd.VehicleObdProfile
import com.domivega.gps_car.ui.state.SettingsUiState

/**
 * @param protocolOptions pairs of (protocol id / enum name, display label)
 * @param scannedDevices pairs of (address, display label)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onApiTokenChange: (String) -> Unit,
    onStartUrlChange: (String) -> Unit,
    onStopUrlChange: (String) -> Unit,
    onSampleUrlChange: (String) -> Unit,
    onSamplesUrlChange: (String) -> Unit,
    onScanQrCode: () -> Unit,
    onTestConnection: () -> Unit = {},
    onClearQrError: () -> Unit = {},
    bleDeviceLabel: String = "",
    connectionStatus: String = "",
    protocolOptions: List<Pair<String, String>> = emptyList(),
    scannedDevices: List<Pair<String, String>> = emptyList(),
    onProtocolSelected: (String) -> Unit = {},
    vehicleObdProfileOptions: List<Pair<String, String>> =
        VehicleObdProfile.entries.map { it.name to it.displayName },
    onVehicleObdProfileSelected: (String) -> Unit = {},
    onVwOdometerDidChange: (String) -> Unit = {},
    onScanClick: () -> Unit = {},
    onDeviceSelected: (address: String, name: String?) -> Unit = { _, _ -> },
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
    fuelTypeOptions: List<Pair<String, String>> = FuelTypePreset.entries.map { it.name to it.displayName },
    onFuelTypeSelected: (String) -> Unit = {},
    onFuelStoichAfrChange: (Double) -> Unit = {},
    onFuelDensityGlChange: (Double) -> Unit = {},
    onEngineDisplacementLChange: (Double) -> Unit = {},
    onEngineVeChange: (Double) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var protocolExpanded by remember { mutableStateOf(false) }
    var vehicleProfileExpanded by remember { mutableStateOf(false) }
    var fuelTypeExpanded by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val selectedProtocolLabel = protocolOptions
        .firstOrNull { it.first == state.obdProtocol }
        ?.second
        ?: state.obdProtocol

    val selectedVehicleProfileLabel = vehicleObdProfileOptions
        .firstOrNull { it.first == state.vehicleObdProfile }
        ?.second
        ?: state.vehicleObdProfile

    val selectedFuelTypeLabel = fuelTypeOptions
        .firstOrNull { it.first == state.fuelType }
        ?.second
        ?: state.fuelType

    val deviceLabel = bleDeviceLabel.ifBlank {
        when {
            state.bleDeviceName.isNotBlank() && state.bleDeviceAddress.isNotBlank() ->
                "${state.bleDeviceName} (${state.bleDeviceAddress})"
            state.bleDeviceAddress.isNotBlank() -> state.bleDeviceAddress
            state.bleDeviceName.isNotBlank() -> state.bleDeviceName
            else -> "None selected"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp) // Generous spacing for touch targets
    ) {
        // API Configuration Section
        Text(
            text = "API Configuration",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Button(
            onClick = onScanQrCode,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Text(text = " Scan Settings via QR", modifier = Modifier.padding(start = 8.dp))
        }

        if (state.qrError.isNotBlank()) {
            Text(
                text = state.qrError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearQrError),
            )
        }

        if (state.carName.isNotBlank() || state.carId.isNotBlank()) {
            Text(
                text = "Provisioned car",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.carName.ifBlank { state.carId },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.carName.isNotBlank() && state.carId.isNotBlank()) {
                Text(
                    text = state.carId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsTextField(
            value = state.apiToken,
            onValueChange = onApiTokenChange,
            label = "API Token"
        )

        SettingsTextField(
            value = state.startUrl,
            onValueChange = onStartUrlChange,
            label = "Start URL"
        )

        SettingsTextField(
            value = state.stopUrl,
            onValueChange = onStopUrlChange,
            label = "Stop URL"
        )

        SettingsTextField(
            value = state.sampleUrl,
            onValueChange = onSampleUrlChange,
            label = "Sample URL"
        )

        SettingsTextField(
            value = state.samplesUrl,
            onValueChange = onSamplesUrlChange,
            label = "Samples URL (Batch)"
        )

        OutlinedButton(
            onClick = onTestConnection,
            enabled = !state.connectionTestInProgress,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = if (state.connectionTestInProgress) "Testing connection…" else "Test connection",
            )
        }

        if (state.connectionTestMessage.isNotBlank()) {
            Text(
                text = state.connectionTestMessage,
                color = if (state.connectionTestIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()

        // OBD / Bluetooth Section
        Text(
            text = "OBD / Bluetooth",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Device",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = deviceLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Status: ${connectionStatus.ifBlank { "Unknown" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (protocolOptions.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = protocolExpanded,
                onExpandedChange = { protocolExpanded = it },
                modifier = Modifier.fillMaxWidth()
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
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = protocolExpanded,
                    onDismissRequest = { protocolExpanded = false }
                ) {
                    protocolOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                protocolExpanded = false
                                onProtocolSelected(id)
                            }
                        )
                    }
                }
            }
        }

        if (vehicleObdProfileOptions.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = vehicleProfileExpanded,
                onExpandedChange = { vehicleProfileExpanded = it },
                modifier = Modifier.fillMaxWidth()
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
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = vehicleProfileExpanded,
                    onDismissRequest = { vehicleProfileExpanded = false }
                ) {
                    vehicleObdProfileOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                vehicleProfileExpanded = false
                                onVehicleObdProfileSelected(id)
                            }
                        )
                    }
                }
            }
        }

        if (state.vehicleObdProfile == VehicleObdProfile.VwMqb.name) {
            SettingsTextField(
                value = state.vwOdometerDid,
                onValueChange = onVwOdometerDidChange,
                label = "VW odometer DID (optional hex)"
            )
            Text(
                text = "4-hex DID override for cluster odometer (e.g. 22A6). Leave empty to try built-in candidates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = {
                showDeviceDialog = true
                onScanClick()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Scan for BLE devices")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onConnectClick,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                enabled = state.bleDeviceAddress.isNotBlank()
            ) {
                Text("Connect")
            }
            OutlinedButton(
                onClick = onDisconnectClick,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Disconnect")
            }
        }

        HorizontalDivider()

        // Vehicle & fuel
        Text(
            text = "Vehicle & fuel",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Fuel rate (L/h) is estimated from MAF and these values. Defaults: E10, 1.0 L. VE is used only if MAF is unavailable (MAP estimate).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (fuelTypeOptions.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = fuelTypeExpanded,
                onExpandedChange = { fuelTypeExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedFuelTypeLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fuel type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelTypeExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = fuelTypeExpanded,
                    onDismissRequest = { fuelTypeExpanded = false }
                ) {
                    fuelTypeOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                fuelTypeExpanded = false
                                onFuelTypeSelected(id)
                            }
                        )
                    }
                }
            }
        }

        SettingsDoubleField(
            value = state.fuelStoichAfr,
            onValueChange = onFuelStoichAfrChange,
            label = "Stoich AFR"
        )

        SettingsDoubleField(
            value = state.fuelDensityGl,
            onValueChange = onFuelDensityGlChange,
            label = "Fuel density (g/L)"
        )

        SettingsDoubleField(
            value = state.engineDisplacementL,
            onValueChange = onEngineDisplacementLChange,
            label = "Engine displacement (L)"
        )

        SettingsDoubleField(
            value = state.engineVe,
            onValueChange = onEngineVeChange,
            label = "Volumetric efficiency (0–1)"
        )
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
                            .heightIn(max = 360.dp)
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
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (nameOrBlank.isNotBlank()) {
                                    Text(
                                        text = address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                TextButton(
                    onClick = {
                        onScanClick()
                    }
                ) {
                    Text("Rescan")
                }
            }
        )
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, // Consistent with automotive theme
        singleLine = true
    )
}

@Composable
private fun SettingsDoubleField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
) {
    var text by remember(value) { mutableStateOf(formatDoubleForField(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        singleLine = true
    )
}

private fun formatDoubleForField(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}
