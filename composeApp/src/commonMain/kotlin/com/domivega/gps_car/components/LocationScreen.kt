package com.domivega.gps_car.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domivega.gps_car.models.LocationViewModel
import com.domivega.gps_car.ui.state.DashboardState

@Composable
fun LocationScreen(
    viewModel: LocationViewModel,
    dashboardState: DashboardState
) {
    // Collect the StateFlow as a Compose State
    val locationData by viewModel.locationState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CarMap(
                modifier = Modifier.fillMaxSize(),
                latitude = locationData.latitude,
                longitude = locationData.longitude
            )
        }

        // Speed and RPM Dials below the map
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Allocate space for the dials
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            VWDial(
                title = "SPEED",
                value = dashboardState.speed.toFloat(),
                maxValue = 240f,
                unit = "km/h",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )

            Spacer(modifier = Modifier.width(16.dp))

            VWDial(
                title = "RPM",
                value = dashboardState.rpm.toFloat(),
                maxValue = 8000f,
                unit = "rpm",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}