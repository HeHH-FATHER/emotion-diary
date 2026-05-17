package com.example.sensordiary.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensordiary.ui.theme.*

@Composable
fun ScanLayer(countdown: Int, shakeFrequency: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617),
                        Color.Black
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Countdown with pulse animation
            PulseCountdownText(countdown)

            Spacer(modifier = Modifier.height(24.dp))

            // Animated Bars - 5 bars instead of 3
            Row(
                modifier = Modifier.height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                PulseBar(color = Rose400, duration = 1000)
                BounceBar(color = Rose500, duration = 800)
                PulseBar(color = Rose100, duration = 1200)
                BounceBar(color = Rose400, duration = 900)
                PulseBar(color = Rose500.copy(alpha = 0.5f), duration = 1100)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "正在进行情绪分析",
                color = Rose400.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            val infiniteTransition = rememberInfiniteTransition(label = "hint_flash")
            val hintAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "hint_alpha"
            )

            Text(
                text = "请拿稳手机，保持自然放松...",
                color = Color.White.copy(alpha = hintAlpha),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun PulseCountdownText(countdown: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "countdown_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_glow"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        // Glow effect background
        Box(
            modifier = Modifier
                .size(180.dp)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Rose400,
                            Rose500.copy(alpha = 0f)
                        )
                    ),
                    shape = RoundedCornerShape(90.dp)
                )
        )
        // Main number
        Text(
            text = if (countdown > 0) countdown.toString() else "0",
            color = Color.White,
            fontSize = 120.sp,
            fontWeight = FontWeight.Thin,
            letterSpacing = (-8).sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

@Composable
fun PulseBar(color: Color, duration: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun BounceBar(color: Color, duration: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val heightScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "height"
    )
    Box(
        modifier = Modifier
            .width(4.dp)
            .fillMaxHeight(heightScale)
            .background(color)
    )
}
