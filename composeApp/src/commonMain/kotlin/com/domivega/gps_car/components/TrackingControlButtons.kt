package com.domivega.gps_car.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

@Composable
fun TrackingControlButtons(
    isTracking: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp) // Space between buttons
    ) {
        // --- Start Button ---
        Button(
            onClick = onStartClick,
            enabled = !isTracking,
            shape = RectangleShape, // Makes the corners sharp (Square)
            // Use MaterialTheme.shapes.medium if you want slightly rounded squares
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .weight(1f) // Takes up 50% of the row
                .aspectRatio(2.5f) // Forces Height to match Width (Square)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Start",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // --- Stop Button ---
        Button(
            onClick = onStopClick,
            enabled = isTracking,
            shape = RectangleShape, // Makes the corners sharp (Square)
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error, // Red for stop
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .weight(1f) // Takes up 50% of the row
                .aspectRatio(2.5f) // Forces Height to match Width (Square)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Stop",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}