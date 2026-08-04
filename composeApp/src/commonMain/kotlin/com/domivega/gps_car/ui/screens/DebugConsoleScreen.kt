package com.domivega.gps_car.ui.screens

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
import kotlin.math.round

@Composable
fun DebugConsoleScreen(
    pidValues: Map<String, Double>,
    pidNames: Map<String, String>,
    connectionStatus: String = "",
    logEntries: List<ObdLogEntry> = emptyList(),
    onClearLog: () -> Unit = {},
) {
    val logListState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            logListState.animateScrollToItem(logEntries.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "OBD Debug",
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
                text = "Status: ${connectionStatus.ifBlank { "Unknown" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearLog) {
                Text("Clear log")
            }
        }

        Text(
            text = "Event log (init + errors)",
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
                        text = "No OBD events yet. Connect an adapter to see init steps.",
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
                        text = "${formatLogTimeUtc(entry.timestampMs)} ${entry.level.name.take(1)} ${entry.message}",
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
            text = "Live PID Values",
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
            Text("Name", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
            Text("Value", modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(pidValues.toList()) { (pid, value) ->
                val name = pidNames[pid] ?: "Unknown"
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

/** UTC wall-clock HH:mm:ss from epoch ms (portable commonMain). */
private fun formatLogTimeUtc(ms: Long): String {
    val s = ((ms / 1000) % 86_400).toInt()
    val hh = (s / 3600).toString().padStart(2, '0')
    val mm = ((s % 3600) / 60).toString().padStart(2, '0')
    val ss = (s % 60).toString().padStart(2, '0')
    return "$hh:$mm:$ss"
}
