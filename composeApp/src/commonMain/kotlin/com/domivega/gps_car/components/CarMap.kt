package com.domivega.gps_car.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CarMap(
    modifier: Modifier = Modifier,
    latitude: Double,
    longitude: Double
)
