package com.domivega.gps_car.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.*
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
import com.domivega.gps_car.components.EngineLoadGauge
import com.domivega.gps_car.components.FuelTankGauge
import com.domivega.gps_car.components.VWDial
import com.domivega.gps_car.ui.state.DashboardState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    state: DashboardState,
    onObdEnabledChange: (Boolean) -> Unit,
    onRetryUpload: () -> Unit = {},
) {
    var confirmDisable by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header / Status
            StatusHeader(
                isTracking = state.isTracking,
                isGpsLocked = state.isGpsLocked,
                ecuConnected = state.ecuConnected
            )

            state.uploadWarning?.let { warning ->
                UploadWarningBanner(
                    message = warning,
                    onRetry = onRetryUpload,
                )
            }

            OdometerBanner(odometerKm = state.odometerKm)

            ClusterExtrasBanner(
                oilTempC = state.oilTempC,
                doorsSummary = state.doorsSummary,
            )

            // Primary Metrics (Speed & RPM)
            Row(
                modifier = Modifier.fillMaxWidth().weight(1.2f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Speed
                VWDial(
                    title = "SPEED",
                    value = state.speed.toFloat(),
                    maxValue = 240f,
                    unit = "km/h",
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                // RPM
                VWDial(
                    title = "RPM",
                    value = state.rpm.toFloat(),
                    maxValue = 8000f,
                    unit = "rpm",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    secondaryColor = Color(0xFFFF00FF) // Magenta for RPM
                )
            }
            
            // Secondary Metrics (Fuel, Load)
            Row(
                modifier = Modifier.fillMaxWidth().weight(0.8f).padding(bottom = 72.dp),
                 horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Fuel Tank
                FuelTankGauge(
                    value = state.fuelLevel.toFloat(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                // Engine Load
                 EngineLoadGauge(
                    value = state.engineLoad.toFloat(),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.obdEnabled) "Enable" else "Disable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = state.obdEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled && ObdEnableGate.disableRequiresConfirmation(state.isTracking)) {
                        confirmDisable = true
                    } else {
                        onObdEnabledChange(enabled)
                    }
                },
            )
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Stop tracking and release the OBD adapter?") },
            text = { Text("Stop tracking and release the OBD adapter?") },
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
fun UploadWarningBanner(
    message: String,
    onRetry: () -> Unit = {},
) {
    var retrying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
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
                        // Brief lock so double-taps don't stack heavy drains.
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
fun StatusHeader(isTracking: Boolean, isGpsLocked: Boolean, ecuConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val statusText = if (isTracking) "ACTIVE TRACKING" else "IDLE"
        val statusColor = if (isTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
             // ECU Status
            val ecuColor = if (ecuConnected) MaterialTheme.colorScheme.secondary else Color.Gray.copy(alpha = 0.5f)
            Surface(
                color = ecuColor.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, ecuColor)
            ) {
                Text(
                    text = "ECU",
                    style = MaterialTheme.typography.labelLarge,
                    color = ecuColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // GPS Status
            Icon(
                imageVector = if (isGpsLocked) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
                contentDescription = "GPS Status",
                tint = if (isGpsLocked) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ClusterExtrasBanner(
    oilTempC: Double?,
    doorsSummary: String?,
) {
    if (oilTempC == null && doorsSummary == null) return
    val parts = buildList {
        if (oilTempC != null && oilTempC.isFinite()) {
            add("Oil ${oilTempC.toInt()}\u00b0C")
        }
        if (doorsSummary != null) add(doorsSummary)
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" \u00b7 "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    )
}

@Composable
fun OdometerBanner(odometerKm: Double?) {
    val label = if (odometerKm != null && odometerKm.isFinite()) {
        val whole = odometerKm.toLong()
        if (odometerKm >= 100.0) {
            "$whole km"
        } else {
            val tenths = ((odometerKm * 10.0) + 0.5).toInt()
            val w = tenths / 10
            val f = tenths % 10
            "$w.$f km"
        }
    } else {
        "— km"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ODOMETER",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String, 
    value: Int, 
    unit: String, 
    modifier: Modifier = Modifier,
    isValuePrimary: Boolean = false
) {
    // Smooth counter animation
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = animatedValue.toString(),
                style = if (isValuePrimary) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
