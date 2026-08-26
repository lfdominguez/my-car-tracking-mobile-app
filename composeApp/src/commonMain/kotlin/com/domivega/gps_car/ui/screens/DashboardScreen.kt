package com.domivega.gps_car.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domivega.gps_car.ObdEnableGate
import com.domivega.gps_car.ui.DashboardPresentation
import com.domivega.gps_car.ui.state.DashboardState
import com.domivega.gps_car.ui.state.Reading
import com.domivega.gps_car.ui.theme.StatusOk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    state: DashboardState,
    onObdEnabledChange: (Boolean) -> Unit,
    onRetryUpload: () -> Unit = {},
) {
    var confirmDisable by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HealthStatusCard(
            state = state,
            onObdEnabledChange = onObdEnabledChange,
            onRetryUpload = onRetryUpload,
            onRequestDisableConfirm = { confirmDisable = true },
        )
        MetricGrid(state)
        SecondaryMetricsRow(state)
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Stop tracking and release the OBD adapter?") },
            text = { Text("The current trip will end, and this app will not reconnect until you enable OBD again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisable = false
                        onObdEnabledChange(false)
                    },
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun HealthStatusCard(
    state: DashboardState,
    onObdEnabledChange: (Boolean) -> Unit,
    onRetryUpload: () -> Unit,
    onRequestDisableConfirm: () -> Unit,
) {
    val trackingColor = if (state.isTracking) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = DashboardPresentation.trackingLabel(state.isTracking),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = trackingColor,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(
                    label = "ECU",
                    active = state.ecuConnected,
                    activeColor = StatusOk,
                )
                StatusChip(
                    label = "GPS",
                    active = state.isGpsLocked,
                    activeColor = MaterialTheme.colorScheme.primary,
                )
            }

            state.uploadWarning?.let { warning ->
                UploadWarningRow(
                    message = warning,
                    onRetry = onRetryUpload,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (state.obdEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = state.obdEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled && ObdEnableGate.disableRequiresConfirmation(state.isTracking)) {
                            onRequestDisableConfirm()
                        } else {
                            onObdEnabledChange(enabled)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    active: Boolean,
    activeColor: Color,
) {
    // Inactive must read as clearly unlit next to a lit chip in daylight, so it
    // drops to a faint outline rather than sitting at half-alpha onSurface.
    val color = if (active) {
        activeColor
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    }
    Surface(
        color = if (active) color.copy(alpha = 0.2f) else Color.Transparent,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UploadWarningRow(
    message: String,
    onRetry: () -> Unit,
) {
    var retrying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = {
                    if (retrying) return@TextButton
                    retrying = true
                    onRetry()
                    scope.launch {
                        delay(1_500)
                        retrying = false
                    }
                },
                enabled = !retrying,
            ) {
                Text(if (retrying) "…" else "Retry")
            }
        }
    }
}

@Composable
private fun MetricGrid(state: DashboardState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricTile(
                label = "Speed",
                reading = state.speed,
                unit = "km/h",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "RPM",
                reading = state.rpm,
                unit = "rpm",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetricTile(
                label = "Fuel",
                reading = state.fuelLevel,
                unit = "%",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Engine load",
                reading = state.engineLoad,
                unit = "%",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Dim level for a reading that is real but older than its freshness budget. */
private const val STALE_ALPHA = 0.45f

@Composable
private fun MetricTile(
    label: String,
    reading: Reading?,
    unit: String,
    modifier: Modifier = Modifier,
) {
    // Stale keeps the number and dims it, so a dropout never moves the gauge.
    // Null means the PID never decoded this session — an em dash, not a zero.
    val valueAlpha = if (reading?.isStale == true) STALE_ALPHA else 1f
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = reading?.value?.toInt()?.toString() ?: "—",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = valueAlpha),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = valueAlpha),
            )
        }
    }
}

@Composable
private fun SecondaryMetricsRow(state: DashboardState) {
    val extras = DashboardPresentation.clusterExtras(state.oilTempC, state.doorsSummary)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Odometer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = DashboardPresentation.odometerLabel(state.odometerKm),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            extras?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
