package com.domivega.gps_car.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FuelTankGauge(
    value: Float, // 0 to 100
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00E5FF) // Cyan/Neon Blue
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 1000)
    )

    Box(modifier = modifier.aspectRatio(0.6f).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Define Tank Shape (Jerry Can style)
            val tankPath = Path().apply {
                // Main body
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w * 0.1f,
                        top = h * 0.2f,
                        right = w * 0.9f,
                        bottom = h * 0.9f,
                        cornerRadius = CornerRadius(w * 0.1f, w * 0.1f)
                    )
                )
                // Spout/Handle area
                moveTo(w * 0.2f, h * 0.2f)
                lineTo(w * 0.3f, h * 0.1f)
                lineTo(w * 0.7f, h * 0.1f)
                lineTo(w * 0.8f, h * 0.2f)
                close()
            }

            // Draw Tank Outline
            drawPath(
                path = tankPath,
                color = color.copy(alpha = 0.5f),
                style = Stroke(width = 4.dp.toPx())
            )

            // Draw Fill
            clipPath(tankPath) {
                val fillHeight = (animatedValue / 100f) * h * 0.7f // Approximate fillable height
                val fillTop = h * 0.9f - fillHeight
                
                drawRect(
                    color = color,
                    topLeft = Offset(0f, fillTop),
                    size = Size(w, fillHeight)
                )
            }
            
            // Add "E" and "F" markers
             // (Simplified visual cues)
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Text(
                text = "${animatedValue.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "FUEL",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
