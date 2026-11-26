package com.domivega.gps_car.ui.screens

import org.jetbrains.compose.resources.stringResource
import com.domivega.gps_car.generated.resources.Res
import com.domivega.gps_car.generated.resources.common_share
import com.domivega.gps_car.generated.resources.common_status
import com.domivega.gps_car.generated.resources.common_unknown
import com.domivega.gps_car.generated.resources.debug_clear
import com.domivega.gps_car.generated.resources.debug_col_name
import com.domivega.gps_car.generated.resources.debug_col_value
import com.domivega.gps_car.generated.resources.debug_event_log
import com.domivega.gps_car.generated.resources.debug_live_pids
import com.domivega.gps_car.generated.resources.debug_no_events
import com.domivega.gps_car.generated.resources.debug_no_trips
import com.domivega.gps_car.generated.resources.debug_past_trips
import com.domivega.gps_car.generated.resources.debug_title
import com.domivega.gps_car.generated.resources.debug_trip_entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domivega.gps_car.obd.ObdLogEntry
import com.domivega.gps_car.obd.ObdLogLevel
import com.domivega.gps_car.obd.TripLogRecord
import com.domivega.gps_car.obd.formatObdLogTimeUtc
import kotlin.math.round

@Composable
fun DebugConsoleScreen(
    pidValues: Map<String, Double>,
    pidNames: Map<String, String>,
    connectionStatus: String = "",
    logEntries: List<ObdLogEntry> = emptyList(),
    tripLogs: List<TripLogRecord> = emptyList(),
    onClearLog: () -> Unit = {},
    onShareLog: () -> Unit = {},
    onShareTripLog: (TripLogRecord) -> Unit = {},
) {
    val logListState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            logListState.animateScrollToItem(logEntries.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(Res.string.debug_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.common_status, connectionStatus.ifBlank { stringResource(Res.string.common_unknown) }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShareLog) {
                Text(stringResource(Res.string.common_share))
            }
            TextButton(onClick = onClearLog) {
                Text(stringResource(Res.string.debug_clear))
            }
        }

        Text(
            text = stringResource(Res.string.debug_event_log),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )

        LazyColumn(
            state = logListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(8.dp),
        ) {
            if (logEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.debug_no_events),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(logEntries, key = { "${it.timestampMs}-${it.message.hashCode()}-${it.level}" }) { entry ->
                    val color = when (entry.level) {
                        ObdLogLevel.ERROR -> MaterialTheme.colorScheme.error
                        ObdLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                        ObdLogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "${formatObdLogTimeUtc(entry.timestampMs)} ${entry.level.name.take(1)} ${entry.message}",
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = stringResource(Res.string.debug_past_trips),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        if (tripLogs.isEmpty()) {
            Text(
                text = stringResource(Res.string.debug_no_trips),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            tripLogs.forEach { record ->
                val durationSec = ((record.endedAtMs - record.startedAtMs) / 1000).coerceAtLeast(0)
                val durationMin = durationSec / 60
                val durationSecRem = durationSec % 60
                val durationLabel = if (durationMin > 0) "${durationMin}m ${durationSecRem}s" else "${durationSecRem}s"
                val timeLabel = formatObdLogTimeUtc(record.startedAtMs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.debug_trip_entry, timeLabel, durationLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onShareTripLog(record) }) {
                        Text(stringResource(Res.string.common_share))
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = stringResource(Res.string.debug_live_pids),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
        ) {
            Text("PID", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.debug_col_name), modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.debug_col_value), modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(pidValues.toList()) { (pid, value) ->
                val name = pidNames[pid] ?: stringResource(Res.string.common_unknown)
                val roundedValue = round(value * 100) / 100.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                ) {
                    Text(pid, modifier = Modifier.weight(0.2f), style = MaterialTheme.typography.bodyMedium)
                    Text(name, modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = roundedValue.toString(),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
