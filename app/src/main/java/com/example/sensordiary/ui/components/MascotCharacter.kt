package com.example.sensordiary.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensordiary.ui.theme.Rose400
import com.example.sensordiary.ui.theme.Rose500
import com.example.sensordiary.ui.theme.Rose100
import com.example.sensordiary.ui.theme.Indigo300
import com.example.sensordiary.ui.theme.Slate400
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class MascotMood {
    HAPPY, LAUGHING, SHY, ANGRY, SLEEPY, SURPRISED
}

enum class MascotAction { CRAWLING, PAUSING, JUMPING, IDLE }

@Composable
fun MascotCharacter(
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val mascotSize = 56.dp
    val mascotSizePx = with(density) { mascotSize.toPx() }
    val scope = rememberCoroutineScope()

    var currentMood by remember { mutableStateOf(MascotMood.HAPPY) }
    var lastInteractionTime by remember { mutableStateOf(0L) }
    var isBouncing by remember { mutableStateOf(false) }
    var showBubble by remember { mutableStateOf(false) }
    var bubbleText by remember { mutableStateOf("") }

    // Action state machine
    var action by remember { mutableStateOf(MascotAction.CRAWLING) }
    var idlePhase by remember { mutableIntStateOf(0) } // 0=yawn, 1=scratch, 2=lookAround
    var movingUp by remember { mutableStateOf(true) }

    // Blink animation
    val blinkTransition = rememberInfiniteTransition(label = "blink")
    val blinkState by blinkTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0
                0f at 3800
                1f at 3900
                0f at 4000
            }
        ),
        label = "blink_state"
    )

    // Crawling transition (variable speed with FastOutSlowInEasing)
    val parentSize = remember { mutableStateOf(IntSize.Zero) }
    val crawlTransition = rememberInfiniteTransition(label = "crawl")
    val crawlProgress by crawlTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 6000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crawl_progress"
    )

    // Bounce animation (click-triggered)
    val bounceAnim by animateFloatAsState(
        targetValue = if (isBouncing) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce"
    )

    // Jump animation (random)
    var isJumping by remember { mutableStateOf(false) }
    val jumpAnim by animateFloatAsState(
        targetValue = if (isJumping) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "jump"
    )

    // Squash + stretch animation
    val squashTarget = when {
        action == MascotAction.JUMPING -> -0.12f // stretch when jumping
        action == MascotAction.CRAWLING && movingUp -> -0.06f // stretch when moving up
        else -> 0.06f // squash when moving down or idle
    }
    val squashAnim by animateFloatAsState(
        targetValue = squashTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "squash"
    )

    // Limb wave animation
    val limbTransition = rememberInfiniteTransition(label = "limbs")
    val limbAngle by limbTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "limb_angle"
    )

    // Body rotation
    val rotationTarget = when {
        movingUp -> -5f
        action == MascotAction.PAUSING || action == MascotAction.IDLE -> 0f
        else -> 5f
    }
    val rotationAnim by animateFloatAsState(
        targetValue = rotationTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "rotation"
    )

    // Idle animation phases
    val idleMouthOpen by animateFloatAsState(
        targetValue = if (action == MascotAction.IDLE && idlePhase == 0) {
            // Yawn: oscillate mouth open
            (sin(System.currentTimeMillis().toFloat() / 300f) + 1f) / 2f
        } else 0f,
        animationSpec = spring(),
        label = "idle_mouth"
    )

    val scratchAnim by animateFloatAsState(
        targetValue = if (action == MascotAction.IDLE && idlePhase == 1) 1f else 0f,
        animationSpec = spring(),
        label = "scratch"
    )

    val lookAroundOffset by animateFloatAsState(
        targetValue = if (action == MascotAction.IDLE && idlePhase == 2) {
            sin(System.currentTimeMillis().toFloat() / 400f) * 3f
        } else 0f,
        animationSpec = spring(),
        label = "look_around"
    )

    // Calculate position with sway
    val crawlHeight = if (parentSize.value.height > 0) {
        parentSize.value.height - mascotSizePx
    } else 0f

    val baseYOffset = crawlHeight * crawlProgress + mascotSizePx / 2
    val jumpOffset = -jumpAnim * 30f
    val yOffset = baseYOffset + jumpOffset

    val swayAmount = sin(crawlProgress * 2 * PI).toFloat() * 8f
    val baseXOffset = mascotSizePx / 2 + 4
    val xOffset = baseXOffset + swayAmount

    // Action state machine driver
    LaunchedEffect(Unit) {
        var crawlDuration = 3000L + Random.nextLong(5000L)
        var pauseDuration = 1000L + Random.nextLong(2000L)

        while (true) {
            delay(100)

            when (action) {
                MascotAction.CRAWLING -> {
                    // Random jump every 10-15 seconds
                    if (Random.nextFloat() < 0.001f && !isJumping) {
                        isJumping = true
                        action = MascotAction.JUMPING
                        delay(800)
                        isJumping = false
                        action = MascotAction.CRAWLING
                        crawlDuration = 3000L + Random.nextLong(5000L)
                    }
                    crawlDuration -= 100
                    if (crawlDuration <= 0) {
                        action = MascotAction.PAUSING
                        idlePhase = Random.nextInt(3)
                    }
                }
                MascotAction.PAUSING -> {
                    pauseDuration -= 100
                    if (pauseDuration <= 0) {
                        action = MascotAction.IDLE
                    }
                }
                MascotAction.IDLE -> {
                    delay(1500)
                    action = MascotAction.CRAWLING
                    crawlDuration = 3000L + Random.nextLong(5000L)
                }
                MascotAction.JUMPING -> {
                    // Handled above
                }
            }
        }
    }

    // Mood auto-reset
    LaunchedEffect(lastInteractionTime) {
        while (true) {
            delay(100)
            if (System.currentTimeMillis() - lastInteractionTime > 5000) {
                if (currentMood != MascotMood.HAPPY) {
                    currentMood = MascotMood.HAPPY
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { parentSize.value = it }
    ) {
        // Mascot character positioned on right edge
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { (xOffset - mascotSizePx / 2).toDp() },
                    y = with(density) { (yOffset - mascotSizePx / 2).toDp() }
                )
                .size(mascotSize)
                .pointerInput(Unit) {
                    detectTapGestures {
                        currentMood = MascotMood.values().random(Random(System.currentTimeMillis()))
                        lastInteractionTime = System.currentTimeMillis()
                        isBouncing = true
                        showBubble = true
                        bubbleText = when (currentMood) {
                            MascotMood.HAPPY -> "嘿！"
                            MascotMood.LAUGHING -> "哈哈"
                            MascotMood.SHY -> "害羞..."
                            MascotMood.ANGRY -> "哼！"
                            MascotMood.SLEEPY -> "zzz..."
                            MascotMood.SURPRISED -> "哇！"
                        }
                        scope.launch {
                            delay(300)
                            isBouncing = false
                        }
                        scope.launch {
                            delay(2000)
                            showBubble = false
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val radius = size.width / 2 - 4

                // Squash + stretch transforms
                val scaleX = 1f - squashAnim
                val scaleY = 1f + squashAnim

                withTransform({
                    scale(scaleX, scaleY, Offset(centerX, centerY))
                    rotate(rotationAnim, Offset(centerX, centerY))
                }) {
                    // Bounce offset
                    val bounceOffset = bounceAnim * 12f

                    // Body
                    val bodyGradient = Brush.radialGradient(
                        colors = listOf(Rose400, Rose500),
                        center = Offset(centerX, centerY + bounceOffset),
                        radius = radius
                    )
                    drawCircle(
                        brush = bodyGradient,
                        radius = radius,
                        center = Offset(centerX, centerY + bounceOffset)
                    )

                    // Body highlight
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = radius * 0.6f,
                        center = Offset(centerX - radius * 0.25f, centerY + bounceOffset - radius * 0.25f)
                    )

                    // Limbs (crawling hands)
                    val limbLength = 12f
                    val limbAngleRad = limbAngle * Math.PI / 180f

                    // Scratch arm override
                    val isScratching = scratchAnim > 0.5f

                    if (!isScratching) {
                        // Left hand (climbing)
                        val leftHandX = centerX - radius * cos(limbAngleRad).toFloat()
                        val leftHandY = centerY + bounceOffset - radius + sin(limbAngleRad).toFloat() * 8
                        drawRoundRect(
                            color = Rose500,
                            topLeft = Offset(leftHandX - 3, leftHandY - limbLength),
                            size = Size(6f, limbLength),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        // Right hand
                        val rightHandX = centerX + radius * cos(limbAngleRad).toFloat()
                        val rightHandY = centerY + bounceOffset - radius - sin(limbAngleRad).toFloat() * 8
                        drawRoundRect(
                            color = Rose500,
                            topLeft = Offset(rightHandX - 3, rightHandY - limbLength),
                            size = Size(6f, limbLength),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    } else {
                        // Left hand normal
                        val leftHandX = centerX - radius * cos(limbAngleRad).toFloat()
                        val leftHandY = centerY + bounceOffset - radius + sin(limbAngleRad).toFloat() * 8
                        drawRoundRect(
                            color = Rose500,
                            topLeft = Offset(leftHandX - 3, leftHandY - limbLength),
                            size = Size(6f, limbLength),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        // Right hand scratching head
                        drawRoundRect(
                            color = Rose500,
                            topLeft = Offset(centerX + radius * 0.3f, centerY + bounceOffset - radius - 8f),
                            size = Size(6f, limbLength),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }

                    // Face based on mood
                    val eyeY = centerY + bounceOffset - radius * 0.2f
                    val eyeSpacing = radius * 0.35f
                    val mouthY = centerY + bounceOffset + radius * 0.25f

                    // Apply lookAround offset to eyes
                    val eyeLookOffset = lookAroundOffset

                    when (currentMood) {
                        MascotMood.HAPPY -> drawHappyFace(centerX, eyeY, eyeSpacing, mouthY, radius, blinkState, eyeLookOffset)
                        MascotMood.LAUGHING -> drawLaughingFace(centerX, eyeY, eyeSpacing, mouthY, radius, blinkState, eyeLookOffset)
                        MascotMood.SHY -> drawShyFace(centerX, eyeY, eyeSpacing, mouthY, radius, blinkState, eyeLookOffset)
                        MascotMood.ANGRY -> drawAngryFace(centerX, eyeY, eyeSpacing, mouthY, radius, eyeLookOffset)
                        MascotMood.SLEEPY -> drawSleepyFace(centerX, eyeY, eyeSpacing, mouthY, radius, eyeLookOffset)
                        MascotMood.SURPRISED -> drawSurprisedFace(centerX, eyeY, eyeSpacing, mouthY, radius, eyeLookOffset)
                    }

                    // Yawn mouth overlay
                    if (idleMouthOpen > 0.1f) {
                        drawOval(
                            color = Color.White.copy(alpha = idleMouthOpen * 0.7f),
                            topLeft = Offset(centerX - 8, mouthY - 4),
                            size = Size(16f, 8f + idleMouthOpen * 8f)
                        )
                    }
                }
            }

            // Speech bubble
            if (showBubble) {
                BubbleText(
                    text = bubbleText,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-24).dp)
                )
            }
        }
    }
}

private fun DrawScope.drawHappyFace(
    centerX: Float, eyeY: Float, eyeSpacing: Float, mouthY: Float, radius: Float, blink: Float, eyeLook: Float = 0f
) {
    if (blink < 0.5f) {
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - eyeSpacing - 5 + eyeLook, eyeY - 8),
            size = Size(10f, 6f),
            style = Stroke(2.5f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX + eyeSpacing - 5 + eyeLook, eyeY - 8),
            size = Size(10f, 6f),
            style = Stroke(2.5f, cap = StrokeCap.Round)
        )
    } else {
        drawLine(Color.White, Offset(centerX - eyeSpacing + eyeLook, eyeY), Offset(centerX - eyeSpacing + 8 + eyeLook, eyeY), 2.5f)
        drawLine(Color.White, Offset(centerX + eyeSpacing + eyeLook, eyeY), Offset(centerX + eyeSpacing + 8 + eyeLook, eyeY), 2.5f)
    }
    drawArc(
        color = Color.White,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - 10, mouthY - 8),
        size = Size(20f, 12f),
        style = Stroke(2.5f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawLaughingFace(
    centerX: Float, eyeY: Float, eyeSpacing: Float, mouthY: Float, radius: Float, blink: Float, eyeLook: Float = 0f
) {
    if (blink < 0.5f) {
        drawArc(
            color = Color.White, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(centerX - eyeSpacing - 5 + eyeLook, eyeY - 8),
            size = Size(10f, 6f), style = Stroke(2.5f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color.White, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(centerX + eyeSpacing - 5 + eyeLook, eyeY - 8),
            size = Size(10f, 6f), style = Stroke(2.5f, cap = StrokeCap.Round)
        )
    }
    drawOval(
        color = Color.White,
        topLeft = Offset(centerX - 12, mouthY - 6),
        size = Size(24f, 16f)
    )
    drawOval(
        color = Indigo300,
        topLeft = Offset(centerX - 8, mouthY - 2),
        size = Size(16f, 8f)
    )
}

private fun DrawScope.drawShyFace(
    centerX: Float, eyeY: Float, eyeSpacing: Float, mouthY: Float, radius: Float, blink: Float, eyeLook: Float = 0f
) {
    if (blink < 0.5f) {
        drawCircle(Color.White, 3f, Offset(centerX - eyeSpacing + eyeLook, eyeY))
        drawCircle(Color.White, 3f, Offset(centerX + eyeSpacing + eyeLook, eyeY))
        drawCircle(Color(0xFF1E293B), 1.5f, Offset(centerX - eyeSpacing + eyeLook, eyeY + 1.5f))
        drawCircle(Color(0xFF1E293B), 1.5f, Offset(centerX + eyeSpacing + eyeLook, eyeY + 1.5f))
    }
    drawArc(
        color = Color.White, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(centerX - 6, mouthY - 4),
        size = Size(12f, 8f), style = Stroke(2f, cap = StrokeCap.Round)
    )
    drawCircle(Color(0xFFFFB4A2).copy(alpha = 0.4f), 8f, Offset(centerX - eyeSpacing - 6, eyeY + 10))
    drawCircle(Color(0xFFFFB4A2).copy(alpha = 0.4f), 8f, Offset(centerX + eyeSpacing + 6, eyeY + 10))
}

private fun DrawScope.drawAngryFace(
    centerX: Float, eyeY: Float, eyeSpacing: Float, mouthY: Float, radius: Float, eyeLook: Float = 0f
) {
    drawCircle(Color.White, 4f, Offset(centerX - eyeSpacing + eyeLook, eyeY))
    drawCircle(Color.White, 4f, Offset(centerX + eyeSpacing + eyeLook, eyeY))
    drawCircle(Color(0xFF1E293B), 2f, Offset(centerX - eyeSpacing + eyeLook, eyeY))
    drawCircle(Color(0xFF1E293B), 2f, Offset(centerX + eyeSpacing + eyeLook, eyeY))
    drawLine(Color.White, Offset(centerX - eyeSpacing - 6 + eyeLook, eyeY - 10), Offset(centerX - eyeSpacing + 4 + eyeLook, eyeY - 6), 2.5f)
    drawLine(Color.White, Offset(centerX + eyeSpacing + 6 + eyeLook, eyeY - 10), Offset(centerX + eyeSpacing - 4 + eyeLook, eyeY - 6), 2.5f)
    drawArc(
        color = Color.White, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(centerX - 8, mouthY + 2),
        size = Size(16f, 8f), style = Stroke(2.5f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSleepyFace(
    centerX: Float, eyeY: Float, eyeSpacing: Float, mouthY: Float, radius: Float, eyeLook: Float = 0f
) {
    drawLine(Color.White, Offset(centerX - eyeSpacing - 5 + eyeLook, eyeY), Offset(centerX - eyeSpacing + 5 + eyeLook, eyeY), 2.5f)
    drawLine(Color.White, Offset(centerX + eyeSpacing - 5 + eyeLook, eyeY), Offset(centerX + eyeSpacing + 5 + eyeLook, eyeY), 2.5f)
    drawArc(
        color = Color.White.copy(alpha = 0.6f), startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(centerX + radius * 0.5f, eyeY - 20),
        size = Size(12f, 8f), style = Stroke(1.5f, cap = StrokeCap.Round)
    )
    drawArc(
        color = Color.White, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(centerX - 5, mouthY - 4),
        size = Size(10f, 6f), style = Stroke(2f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSurprisedFace(
    centerX: Float, eyeY: Float, eyeSpacing: Float, mouthY: Float, radius: Float, eyeLook: Float = 0f
) {
    drawCircle(Color.White, 5f, Offset(centerX - eyeSpacing + eyeLook, eyeY))
    drawCircle(Color.White, 5f, Offset(centerX + eyeSpacing + eyeLook, eyeY))
    drawCircle(Color(0xFF1E293B), 2.5f, Offset(centerX - eyeSpacing + eyeLook, eyeY))
    drawCircle(Color(0xFF1E293B), 2.5f, Offset(centerX + eyeSpacing + eyeLook, eyeY))
    drawCircle(Color.White, 7f, Offset(centerX, mouthY + 2))
    drawCircle(Indigo300, 4f, Offset(centerX, mouthY + 2))
}

@Composable
fun BubbleText(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(y = (-8).dp)
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = com.example.sensordiary.ui.theme.Slate800,
            shadowElevation = 4.dp
        ) {
            androidx.compose.material3.Text(
                text = text,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Canvas(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 4.dp)) {
            val path = Path()
            path.moveTo(size.width / 2 - 4, 0f)
            path.lineTo(size.width / 2, 8f)
            path.lineTo(size.width / 2 + 4, 0f)
            path.close()
            drawPath(path, color = com.example.sensordiary.ui.theme.Slate800)
        }
    }
}
