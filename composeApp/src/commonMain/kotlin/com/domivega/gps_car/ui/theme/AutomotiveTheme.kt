package com.domivega.gps_car.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// High-contrast accent colors
val NeonBlue = Color(0xFF00E5FF)
val NeonAmber = Color(0xFFFFC400)
val DarkBackground = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF1C1C1E)
val OnSurfaceLight = Color(0xFFE0E0E0)
val ErrorRed = Color(0xFFCF6679)

private val AutomotiveDarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004B57),
    onPrimaryContainer = NeonBlue,
    secondary = NeonAmber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF524200),
    onSecondaryContainer = NeonAmber,
    background = DarkBackground,
    onBackground = OnSurfaceLight,
    surface = SurfaceDark,
    onSurface = OnSurfaceLight,
    error = ErrorRed
)

// Large Rounded Corners as requested
val AutomotiveShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Large, legible fonts
val AutomotiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

@Composable
fun AutomotiveTheme(
    content: @Composable () -> Unit
) {
    // Force Dark Mode-first as requested
    MaterialTheme(
        colorScheme = AutomotiveDarkColorScheme,
        shapes = AutomotiveShapes,
        typography = AutomotiveTypography,
        content = content
    )
}
