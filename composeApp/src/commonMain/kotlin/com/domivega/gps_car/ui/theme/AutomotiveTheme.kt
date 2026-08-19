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

// Dark-first cockpit tokens: cooler surfaces, one cyan accent
val AccentCyan = Color(0xFF5EE0F0)
val DarkBackground = Color(0xFF0B0F12)
val SurfaceDark = Color(0xFF151A1E)
val SurfaceContainer = Color(0xFF1C2328)
val OnSurfaceLight = Color(0xFFE6EEF2)
val ErrorRed = Color(0xFFFF8A80)

private val AutomotiveDarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF0E3A42),
    onPrimaryContainer = AccentCyan,
    secondary = Color(0xFF9BB0B8),
    onSecondary = Color(0xFF101417),
    secondaryContainer = Color(0xFF243036),
    onSecondaryContainer = Color(0xFFD5E3E8),
    background = DarkBackground,
    onSurface = OnSurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceDark,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = Color(0xFFB5C4CA),
    error = ErrorRed,
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3A474D),
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
