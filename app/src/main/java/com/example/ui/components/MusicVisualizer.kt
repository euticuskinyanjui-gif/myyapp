package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun MusicVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    color: Color = Color(0xFF00F0FF)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Visualizer")
    
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val barSeeds = remember { List(barCount) { Random.nextFloat() * 0.6f + 0.4f } }
    val barOffsets = remember { List(barCount) { Random.nextFloat() * (Math.PI * 2).toFloat() } }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (width - totalSpacing) / barCount

        for (i in 0 until barCount) {
            val seed = barSeeds[i]
            val offset = barOffsets[i]
            
            val targetHeightFraction = if (isPlaying) {
                val wave = kotlin.math.sin((animatedPhase * Math.PI * 2 + offset).toDouble()).toFloat()
                (wave + 1f) / 2f * 0.8f + 0.2f
            } else {
                0.12f + (i % 3) * 0.04f
            }

            val barHeight = height * targetHeightFraction * seed
            val top = height - barHeight
            val left = i * (barWidth + spacing)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.4f),
                        color
                    )
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
