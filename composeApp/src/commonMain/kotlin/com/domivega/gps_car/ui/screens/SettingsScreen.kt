package com.domivega.gps_car.ui.screens

import com.domivega.gps_car.generated.resources.set_scan_qr

import org.jetbrains.compose.resources.stringResource
import com.domivega.gps_car.generated.resources.Res
import com.domivega.gps_car.generated.resources.set_backend
import com.domivega.gps_car.generated.resources.set_backend_hint
import com.domivega.gps_car.generated.resources.set_density
import com.domivega.gps_car.generated.resources.set_displacement
import com.domivega.gps_car.generated.resources.set_f_abs_load
import com.domivega.gps_car.generated.resources.set_f_ambient
import com.domivega.gps_car.generated.resources.set_f_baro
import com.domivega.gps_car.generated.resources.set_f_coolant
import com.domivega.gps_car.generated.resources.set_f_dist_clear
import com.domivega.gps_car.generated.resources.set_f_fuel_level
import com.domivega.gps_car.generated.resources.set_f_fuel_rate
import com.domivega.gps_car.generated.resources.set_f_iat
import com.domivega.gps_car.generated.resources.set_f_lambda
import com.domivega.gps_car.generated.resources.set_f_load
import com.domivega.gps_car.generated.resources.set_f_ltft
import com.domivega.gps_car.generated.resources.set_f_maf
import com.domivega.gps_car.generated.resources.set_f_map
import com.domivega.gps_car.generated.resources.set_f_motion
import com.domivega.gps_car.generated.resources.set_f_odometer
import com.domivega.gps_car.generated.resources.set_f_pedal
import com.domivega.gps_car.generated.resources.set_f_runtime
import com.domivega.gps_car.generated.resources.set_f_stft
import com.domivega.gps_car.generated.resources.set_f_voltage
import com.domivega.gps_car.generated.resources.set_fuel
import com.domivega.gps_car.generated.resources.set_fuel_class
import com.domivega.gps_car.generated.resources.set_fuel_hint
import com.domivega.gps_car.generated.resources.set_fuel_type
import com.domivega.gps_car.generated.resources.set_hv
import com.domivega.gps_car.generated.resources.set_ping_url
import com.domivega.gps_car.generated.resources.set_provisioned_car
import com.domivega.gps_car.generated.resources.set_samples_url
import com.domivega.gps_car.generated.resources.set_start_url
import com.domivega.gps_car.generated.resources.set_stoich
import com.domivega.gps_car.generated.resources.set_stop_url
import com.domivega.gps_car.generated.resources.set_tank
import com.domivega.gps_car.generated.resources.set_test
import com.domivega.gps_car.generated.resources.set_testing
import com.domivega.gps_car.generated.resources.set_token
import com.domivega.gps_car.generated.resources.set_upload_fields
import com.domivega.gps_car.generated.resources.set_upload_fields_hint
import com.domivega.gps_car.generated.resources.set_ve
import com.domivega.gps_car.ui.resolve
import com.domivega.gps_car.ui.label

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
import com.domivega.gps_car.fuel.FuelClass
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
    onSamplesUrlChange: (String) -> Unit,
    onPingUrlChange: (String) -> Unit = {},
    onScanQrCode: () -> Unit,
    onTestConnection: () -> Unit = {},
    onClearQrError: () -> Unit = {},
    onFuelClassSelected: (String) -> Unit = {},
    onFuelTypeSelected: (String) -> Unit = {},
    onFuelStoichAfrChange: (Double) -> Unit = {},
    onFuelDensityGlChange: (Double) -> Unit = {},
    onEngineDisplacementLChange: (Double) -> Unit = {},
    onEngineVeChange: (Double) -> Unit = {},
    onTankCapacityLChange: (Double) -> Unit = {},
    onBatteryCapacityKwhChange: (Double) -> Unit = {},
    onSampleUploadFieldFlagsChange: (SampleUploadFieldFlags) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var fuelClassExpanded by remember { mutableStateOf(false) }
    var fuelTypeExpanded by remember { mutableStateOf(false) }

    val selectedFuelClass = FuelClass.fromName(state.fuelClass)
    val fuelClassOptions = FuelClass.entries.map { it.name to it.label() }
    val gradeOptions = FuelTypePreset.gradesFor(selectedFuelClass)
        .map { it.name to it.label() }

    val selectedFuelClassLabel = fuelClassOptions
        .firstOrNull { it.first == selectedFuelClass.name }
        ?.second
        ?: selectedFuelClass.label()

    val selectedFuelTypeLabel = gradeOptions
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
            title = stringResource(Res.string.set_backend),
            supporting = stringResource(Res.string.set_backend_hint),
        ) {
        Button(
            onClick = onScanQrCode,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Text(text = stringResource(Res.string.set_scan_qr), modifier = Modifier.padding(start = 8.dp))
        }

        if (!state.qrError.isBlank()) {
            Text(
                text = state.qrError.resolve(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearQrError),
            )
        }

        if (state.carName.isNotBlank() || state.carId.isNotBlank()) {
            Text(
                text = stringResource(Res.string.set_provisioned_car),
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
            label = stringResource(Res.string.set_token)
        )

        SettingsTextField(
            value = state.startUrl,
            onValueChange = onStartUrlChange,
            label = stringResource(Res.string.set_start_url)
        )

        SettingsTextField(
            value = state.stopUrl,
            onValueChange = onStopUrlChange,
            label = stringResource(Res.string.set_stop_url)
        )

        SettingsTextField(
            value = state.samplesUrl,
            onValueChange = onSamplesUrlChange,
            label = stringResource(Res.string.set_samples_url)
        )

        SettingsTextField(
            value = state.pingUrl,
            onValueChange = onPingUrlChange,
            label = stringResource(Res.string.set_ping_url)
        )

        OutlinedButton(
            onClick = onTestConnection,
            enabled = !state.connectionTestInProgress,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = if (state.connectionTestInProgress) stringResource(Res.string.set_testing) else stringResource(Res.string.set_test),
            )
        }

        if (!state.connectionTestMessage.isBlank()) {
            Text(
                text = state.connectionTestMessage.resolve(),
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
            title = stringResource(Res.string.set_fuel),
            supporting = stringResource(Res.string.set_fuel_hint),
        ) {
            ExposedDropdownMenuBox(
                expanded = fuelClassExpanded,
                onExpandedChange = { fuelClassExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedFuelClassLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.set_fuel_class)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelClassExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = fuelClassExpanded,
                    onDismissRequest = { fuelClassExpanded = false }
                ) {
                    fuelClassOptions.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                fuelClassExpanded = false
                                onFuelClassSelected(id)
                            }
                        )
                    }
                }
            }

        if (gradeOptions.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = fuelTypeExpanded,
                onExpandedChange = { fuelTypeExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedFuelTypeLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.set_fuel_type)) },
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
                    gradeOptions.forEach { (id, label) ->
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
            label = stringResource(Res.string.set_stoich)
        )

        SettingsDoubleField(
            value = state.fuelDensityGl,
            onValueChange = onFuelDensityGlChange,
            label = stringResource(Res.string.set_density)
        )

        SettingsDoubleField(
            value = state.engineDisplacementL,
            onValueChange = onEngineDisplacementLChange,
            label = stringResource(Res.string.set_displacement)
        )

        SettingsDoubleField(
            value = state.engineVe,
            onValueChange = onEngineVeChange,
            label = stringResource(Res.string.set_ve)
        )

        SettingsDoubleField(
            value = state.tankCapacityL,
            onValueChange = onTankCapacityLChange,
            label = stringResource(Res.string.set_tank)
        )

        SettingsDoubleField(
            value = state.batteryCapacityKwh,
            onValueChange = onBatteryCapacityKwhChange,
            label = stringResource(Res.string.set_hv)
        )
        }

        SettingsSection(
            title = stringResource(Res.string.set_upload_fields),
            supporting = stringResource(Res.string.set_upload_fields_hint),
        ) {
        val flags = state.sampleUploadFieldFlags
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_fuel_rate),
            checked = flags.fuelConsumptionRate,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(fuelConsumptionRate = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_load),
            checked = flags.engineLoadPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(engineLoadPct = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_abs_load),
            checked = flags.absoluteEngineLoadPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(absoluteEngineLoadPct = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_stft),
            checked = flags.shortTermFuelTrimPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(shortTermFuelTrimPct = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_ltft),
            checked = flags.longTermFuelTrimPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(longTermFuelTrimPct = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_fuel_level),
            checked = flags.fuelLevelPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(fuelLevelPct = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_pedal),
            checked = flags.acceleratorPedalPct,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(acceleratorPedalPct = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_ambient),
            checked = flags.ambientAirTempC,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(ambientAirTempC = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_odometer),
            checked = flags.odometerValueKm,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(odometerValueKm = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_coolant),
            checked = flags.engineCoolantTempC,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(engineCoolantTempC = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_map),
            checked = flags.manifoldAbsolutePressureKpa,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(manifoldAbsolutePressureKpa = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_voltage),
            checked = flags.controlModuleVoltage,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(controlModuleVoltage = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_runtime),
            checked = flags.engineOnTime,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(engineOnTime = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_maf),
            checked = flags.massAirFlow,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(massAirFlow = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_lambda),
            checked = flags.lambdaCmd,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(lambdaCmd = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_baro),
            checked = flags.atmosphericPressure,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(atmosphericPressure = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_iat),
            checked = flags.intakeAirTemperature,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(intakeAirTemperature = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_dist_clear),
            checked = flags.distanceSinceDtcClearKm,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(distanceSinceDtcClearKm = it)) },
        )
        UploadFieldSwitch(
            label = stringResource(Res.string.set_f_motion),
            checked = flags.motion,
            onCheckedChange = { onSampleUploadFieldFlagsChange(flags.copy(motion = it)) },
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
            // Accept the decimal comma used in German and other locales.
            newText.trim().replace(',', '.').toDoubleOrNull()?.let(onValueChange)
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
