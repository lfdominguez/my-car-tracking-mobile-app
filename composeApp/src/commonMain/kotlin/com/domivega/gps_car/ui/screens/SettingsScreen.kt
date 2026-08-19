package com.domivega.gps_car.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domivega.gps_car.fuel.FuelTypePreset
import com.domivega.gps_car.settings.SampleUploadFieldFlags
import com.domivega.gps_car.ui.state.SettingsUiState

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
    fuelTypeOptions: List<Pair<String, String>> = FuelTypePreset.entries.map { it.name to it.displayName },
    onFuelTypeSelected: (String) -> Unit = {},
    onFuelStoichAfrChange: (Double) -> Unit = {},
    onFuelDensityGlChange: (Double) -> Unit = {},
    onEngineDisplacementLChange: (Double) -> Unit = {},
    onEngineVeChange: (Double) -> Unit = {},
    onTankCapacityLChange: (Double) -> Unit = {},
    onSampleUploadFieldFlagsChange: (SampleUploadFieldFlags) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var fuelTypeExpanded by remember { mutableStateOf(false) }

    val selectedFuelTypeLabel = fuelTypeOptions
        .firstOrNull { it.first == state.fuelType }
        ?.second
        ?: state.fuelType

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(
            title = "Backend",
            supporting = "QR provisioning, token, and ingest URLs",
        ) {
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
        }

        SettingsSection(
            title = "Fuel",
            supporting = "Fuel rate (L/h) is estimated from MAF and these values. Defaults: E10, 1.0 L. VE is used only if MAF is unavailable (MAP estimate).",
        ) {
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

        SettingsDoubleField(
            value = state.tankCapacityL,
            onValueChange = onTankCapacityLChange,
            label = "Tank capacity (L, 0 = unknown)"
        )
        }

        SettingsSection(
            title = "Upload fields",
            supporting = "Always sent: GPS lat/lon, velocity, RPM (plus tracking id and accuracy).",
        ) {
        val flags = state.sampleUploadFieldFlags
        UploadFieldSwitch(
            label = "Fuel consumption rate (L/h)",
            checked = flags.fuelConsumptionRate,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(fuelConsumptionRate = it)) },
        )
        UploadFieldSwitch(
            label = "Engine load %",
            checked = flags.engineLoadPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(engineLoadPct = it)) },
        )
        UploadFieldSwitch(
            label = "Absolute engine load %",
            checked = flags.absoluteEngineLoadPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(absoluteEngineLoadPct = it)) },
        )
        UploadFieldSwitch(
            label = "Short-term fuel trim %",
            checked = flags.shortTermFuelTrimPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(shortTermFuelTrimPct = it)) },
        )
        UploadFieldSwitch(
            label = "Long-term fuel trim %",
            checked = flags.longTermFuelTrimPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(longTermFuelTrimPct = it)) },
        )
        UploadFieldSwitch(
            label = "Fuel level %",
            checked = flags.fuelLevelPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(fuelLevelPct = it)) },
        )
        UploadFieldSwitch(
            label = "Accelerator pedal %",
            checked = flags.acceleratorPedalPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(acceleratorPedalPct = it)) },
        )
        UploadFieldSwitch(
            label = "Ambient air temp °C",
            checked = flags.ambientAirTempC,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(ambientAirTempC = it)) },
        )
        UploadFieldSwitch(
            label = "Odometer km",
            checked = flags.odometerValueKm,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(odometerValueKm = it)) },
        )
        UploadFieldSwitch(
            label = "Coolant temp °C",
            checked = flags.engineCoolantTempC,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(engineCoolantTempC = it)) },
        )
        UploadFieldSwitch(
            label = "MAP kPa",
            checked = flags.manifoldAbsolutePressureKpa,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(manifoldAbsolutePressureKpa = it)) },
        )
        UploadFieldSwitch(
            label = "Control module voltage",
            checked = flags.controlModuleVoltage,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(controlModuleVoltage = it)) },
        )
        UploadFieldSwitch(
            label = "Engine on time",
            checked = flags.engineOnTime,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(engineOnTime = it)) },
        )
        UploadFieldSwitch(
            label = "Mass air flow",
            checked = flags.massAirFlow,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(massAirFlow = it)) },
        )
        UploadFieldSwitch(
            label = "Lambda commanded",
            checked = flags.lambdaCmd,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(lambdaCmd = it)) },
        )
        UploadFieldSwitch(
            label = "Atmospheric pressure",
            checked = flags.atmosphericPressure,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(atmosphericPressure = it)) },
        )
        UploadFieldSwitch(
            label = "Intake air temperature",
            checked = flags.intakeAirTemperature,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(intakeAirTemperature = it)) },
        )
        }
    }
}

@Composable
private fun UploadFieldSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
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
