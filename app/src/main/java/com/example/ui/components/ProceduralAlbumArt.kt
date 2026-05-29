package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ProceduralAlbumArt(
    category: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isMini: Boolean = false
) {
    // Rotating effect when playing
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val currentRotation = if (isPlaying) rotation else 0f

    // Choose colors based on category
    val (colors, styleText) = remember(category) {
        when (category) {
            "Lofi Beats" -> listOf(Color(0xFF2C1B4D), Color(0xFF5F4B8B), Color(0xFFB19CD9)) to "☕ LOFI"
            "Synthwave" -> listOf(Color(0xFF0F051D), Color(0xFFE1007E), Color(0xFF00F0FF)) to "⚡ SYNTH"
            "Chill Acoustic" -> listOf(Color(0xFF352B1E), Color(0xFFC68B59), Color(0xFFF3E9DC)) to "🍃 ACST"
            "Workout Mix" -> listOf(Color(0xFF0A1C14), Color(0xFF22C55E), Color(0xFF0F172A)) to "🏃 GYM"
            else -> listOf(Color(0xFF1E293B), Color(0xFF64748B), Color(0xFFCBD5E1)) to "🎵 SONG"
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(if (isMini) 12.dp else 24.dp))
            .background(Brush.linearGradient(colors))
            .rotate(if (isPlaying && !isMini) currentRotation else 0f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f

            // Draw a spinning vinyl styling
            if (!isMini) {
                // outer vinyl ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = radius * 0.95f,
                    style = Stroke(width = size.width * 0.04f)
                )

                // sound grooves
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius * 0.82f,
                    style = Stroke(width = size.width * 0.01f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius * 0.65f,
                    style = Stroke(width = size.width * 0.01f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius * 0.48f,
                    style = Stroke(width = size.width * 0.01f)
                )

                // decorative elements depending on category
                when (category) {
                    "Synthwave" -> {
                        drawCircle(
                            color = Color(0xFF00F0FF).copy(alpha = 0.3f),
                            radius = radius * 0.3f,
                            style = Stroke(width = 4f)
                        )
                    }
                    "Lofi Beats" -> {
                        drawCircle(
                            color = Color(0xFFF3E9DC).copy(alpha = 0.4f),
                            radius = radius * 0.25f
                        )
                    }
                    "Chill Acoustic" -> {
                        drawCircle(
                            color = Color(0xFFF3E9DC).copy(alpha = 0.25f),
                            radius = radius * 0.35f,
                            style = Stroke(width = 6f)
                        )
                    }
                    "Workout Mix" -> {
                        val sizeSq = radius * 0.4f
                        drawRect(
                            color = Color(0xFF22C55E).copy(alpha = 0.3f),
                            topLeft = Offset(center.x - sizeSq / 2f, center.y - sizeSq / 2f),
                            size = androidx.compose.ui.geometry.Size(sizeSq, sizeSq),
                            style = Stroke(width = 4f)
                        )
                    }
                }

                // Vinyl center label
                drawCircle(
                    color = colors[1],
                    radius = radius * 0.22f
                )
                drawCircle(
                    color = Color.Black,
                    radius = radius * 0.06f
                )
            } else {
                // Mini item decorative
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = radius * 0.7f,
                    style = Stroke(width = 3f)
                )
            }
        }

        if (isMini) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
