package com.domivega.gps_car.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VWDial(
    value: Float,
    maxValue: Float = 240f,
    title: String,
    unit: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF00BFFF), // Deep Sky Blue for futuristic look
    secondaryColor: Color = Color(0xFFFF0055) // Red/Magenta for needle/highlights
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, maxValue),
        animationSpec = tween(durationMillis = 500)
    )

    Box(modifier = modifier.aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2
            val startAngle = 135f
            val sweepAngle = 270f

            // 1. Background Arc (Track)
            drawArc(
                color = primaryColor.copy(alpha = 0.2f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // 2. Active Arc (Progress)
            val progressAngle = (animatedValue / maxValue) * sweepAngle
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to primaryColor.copy(alpha = 0.5f),
                    0.5f to primaryColor,
                    1.0f to secondaryColor,
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = progressAngle,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // 3. Ticks
            val tickCount = 10
            for (i in 0..tickCount) {
                val angle = startAngle + (sweepAngle / tickCount) * i
                val angleRad = Math.toRadians(angle.toDouble())
                val innerRadius = radius - 20.dp.toPx()
                val outerRadius = radius - 8.dp.toPx()
                
                val start = Offset(
                    (center.x + innerRadius * cos(angleRad)).toFloat(),
                    (center.y + innerRadius * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    (center.x + outerRadius * cos(angleRad)).toFloat(),
                    (center.y + outerRadius * sin(angleRad)).toFloat()
                )
                
                drawLine(
                    color = if (i % 2 == 0) primaryColor else primaryColor.copy(alpha = 0.5f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 2 == 0) 3.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 4. Digital Readout
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor.copy(alpha = 0.8f)
            )
            Text(
                text = animatedValue.toInt().toString(),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = primaryColor
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = primaryColor.copy(alpha = 0.6f)
            )
        }
    }
}
