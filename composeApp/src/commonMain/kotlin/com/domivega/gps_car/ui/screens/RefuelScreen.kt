package com.domivega.gps_car.ui.screens

import org.jetbrains.compose.resources.stringResource
import com.domivega.gps_car.generated.resources.Res
import com.domivega.gps_car.generated.resources.common_saving
import com.domivega.gps_car.generated.resources.refuel_currency
import com.domivega.gps_car.generated.resources.refuel_datetime
import com.domivega.gps_car.generated.resources.refuel_full
import com.domivega.gps_car.generated.resources.refuel_litres
import com.domivega.gps_car.generated.resources.refuel_odometer
import com.domivega.gps_car.generated.resources.refuel_price
import com.domivega.gps_car.generated.resources.refuel_quantity
import com.domivega.gps_car.generated.resources.refuel_reading
import com.domivega.gps_car.generated.resources.refuel_receipt
import com.domivega.gps_car.generated.resources.refuel_receipt_hint
import com.domivega.gps_car.generated.resources.refuel_save
import com.domivega.gps_car.generated.resources.refuel_scan
import com.domivega.gps_car.generated.resources.refuel_station
import com.domivega.gps_car.generated.resources.refuel_title
import com.domivega.gps_car.generated.resources.refuel_total
import com.domivega.gps_car.ui.resolve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.domivega.gps_car.ui.state.RefuelUiState

@Composable
fun RefuelScreen(
    state: RefuelUiState,
    onScanReceipt: () -> Unit,
    onEdit: ((RefuelUiState) -> RefuelUiState) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSection(
            title = stringResource(Res.string.refuel_receipt),
            supporting = stringResource(Res.string.refuel_receipt_hint),
        ) {
            FilledTonalButton(
                onClick = onScanReceipt,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.reading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.reading) stringResource(Res.string.refuel_reading) else stringResource(Res.string.refuel_scan))
            }
            if (state.receiptText.isNotEmpty()) {
                Text(
                    text = state.receiptText.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.refuel_title)) {
            SettingsTextField(
                value = state.filledAt,
                onValueChange = { v -> onEdit { it.copy(filledAt = v) } },
                label = stringResource(Res.string.refuel_datetime),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("L" to stringResource(Res.string.refuel_litres), "kWh" to "kWh").forEachIndexed { i, (unit, label) ->
                    SegmentedButton(
                        selected = state.unit == unit,
                        onClick = { onEdit { it.copy(unit = unit) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    ) { Text(label) }
                }
            }
            NumberField(state.quantity, { v -> onEdit { it.copy(quantity = v) } }, stringResource(Res.string.refuel_quantity, state.unit))
            NumberField(state.pricePerUnit, { v -> onEdit { it.copy(pricePerUnit = v) } }, stringResource(Res.string.refuel_price, state.unit))
            NumberField(state.totalCost, { v -> onEdit { it.copy(totalCost = v) } }, stringResource(Res.string.refuel_total))
            SettingsTextField(
                value = state.currency,
                onValueChange = { v -> onEdit { it.copy(currency = v) } },
                label = stringResource(Res.string.refuel_currency),
            )
            SettingsTextField(
                value = state.station,
                onValueChange = { v -> onEdit { it.copy(station = v) } },
                label = stringResource(Res.string.refuel_station),
            )
            NumberField(state.odometerKm, { v -> onEdit { it.copy(odometerKm = v) } }, stringResource(Res.string.refuel_odometer))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.refuel_full),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = state.fullTank,
                    onCheckedChange = { v -> onEdit { it.copy(fullTank = v) } },
                )
            }
            Button(
                onClick = onSave,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) stringResource(Res.string.common_saving) else stringResource(Res.string.refuel_save))
            }
            if (!state.message.isBlank()) {
                Text(
                    text = state.message.resolve(),
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
