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
fun EngineLoadGauge(
    value: Float, // 0 to 100
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF9100) // Orange
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 500)
    )

    Box(modifier = modifier.aspectRatio(0.8f).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Define Engine Shape (Stylized Engine Block)
            val enginePath = Path().apply {
                // Main Block
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w * 0.2f,
                        top = h * 0.3f,
                        right = w * 0.8f,
                        bottom = h * 0.8f,
                        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                    )
                )
                
                // Top components (Heads/Valves)
                moveTo(w * 0.25f, h * 0.3f)
                lineTo(w * 0.25f, h * 0.2f)
                lineTo(w * 0.35f, h * 0.2f)
                lineTo(w * 0.35f, h * 0.3f)
                close()

                moveTo(w * 0.65f, h * 0.3f)
                lineTo(w * 0.65f, h * 0.2f)
                lineTo(w * 0.75f, h * 0.2f)
                lineTo(w * 0.75f, h * 0.3f)
                close()
                
                // Side Fan/Belt
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        left = w * 0.75f,
                        top = h * 0.4f,
                        right = w * 0.85f,
                        bottom = h * 0.7f
                    )
                )
                
                // Left Intake
                 moveTo(w * 0.2f, h * 0.45f)
                 lineTo(w * 0.1f, h * 0.45f)
                 lineTo(w * 0.1f, h * 0.55f)
                 lineTo(w * 0.2f, h * 0.55f)
                 close()
            }

            // Draw Outline
            drawPath(
                path = enginePath,
                color = color.copy(alpha = 0.5f),
                style = Stroke(width = 4.dp.toPx())
            )

            // Draw Fill
            clipPath(enginePath) {
                val fillHeight = (animatedValue / 100f) * h
                val fillTop = h - fillHeight
                
                drawRect(
                    color = color,
                    topLeft = Offset(0f, fillTop),
                    size = Size(w, fillHeight)
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             Text(
                text = "${animatedValue.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "LOAD",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
