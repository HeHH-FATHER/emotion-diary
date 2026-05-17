package com.example.sensordiary.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensordiary.model.MoodRecord
import com.example.sensordiary.ui.theme.*
import com.example.sensordiary.viewmodel.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    var revealedRecordId by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()

    // Collapse revealed item on scroll
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            revealedRecordId = -1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    revealedRecordId = -1
                })
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBackground)
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            item {
                Header(viewModel.dateLabel)
                Spacer(modifier = Modifier.height(40.dp))
                SensorGrid(
                    viewModel.lightIntensity,
                    viewModel.ambientDecibels,
                    viewModel.hasAudioPermission,
                    viewModel.isLightSensorSupported,
                    viewModel.isGyroSensorSupported,
                    viewModel.activityState,
                    viewModel.isScreenOn,
                    viewModel.voicePitch,
                    viewModel.voiceTone,
                    onRequestPermission = { viewModel.requestAudioPermission() }
                )
                Spacer(modifier = Modifier.height(40.dp))
                SectionTitle("今日检测记录")
                Spacer(modifier = Modifier.height(24.dp))
            }
            items(viewModel.moodRecords, key = { it.id }) { record ->
                SwipeToRevealDelete(
                    isRevealed = revealedRecordId == record.id,
                    onReveal = { revealedRecordId = record.id },
                    onCollapse = { revealedRecordId = -1 },
                    onDeleteClick = { viewModel.toggleDeleteConfirmDialog(true, record) }
                ) {
                    LogCard(record)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                DisclaimerTip()
                Spacer(modifier = Modifier.height(60.dp)) // Extra space for bottom nav
            }
        }

        if (viewModel.showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleDeleteConfirmDialog(false) },
                title = { Text("确认删除", fontWeight = FontWeight.Bold) },
                text = { Text("确定要删除这条检测记录吗？此操作无法撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.confirmDeleteRecord()
                            revealedRecordId = -1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("确认删除", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleDeleteConfirmDialog(false) }) {
                        Text("取消", color = Slate400, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun SwipeToRevealDelete(
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    onDeleteClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxRevealPx = with(density) { -80.dp.toPx() }
    var offsetX by remember { mutableStateOf(0f) }

    LaunchedEffect(isRevealed) {
        offsetX = if (isRevealed) maxRevealPx else 0f
    }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
    ) {
        // Background Delete Button
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Red.copy(alpha = 0.8f))
                .clickable { onDeleteClick() }
                .padding(end = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color.White
            )
        }

        // Foreground Content
        Surface(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < maxRevealPx / 2) {
                                offsetX = maxRevealPx
                                onReveal()
                            } else {
                                offsetX = 0f
                                onCollapse()
                            }
                        },
                        onDragCancel = {
                            offsetX = 0f
                            onCollapse()
                        },
                        onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(maxRevealPx, 0f)
                        }
                    )
                }
                .pointerInput(isRevealed) {
                    detectTapGestures(onTap = {
                        onCollapse()
                    })
                },
            shape = RoundedCornerShape(32.dp),
            color = CardBackground
        ) {
            content()
        }
    }
}

@Composable
fun Header(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "情绪日记",
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF5C4A3D),
                            Color(0xFF8B6F5C),
                            Color(0xFF5C4A3D)
                        )
                    ),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-2).sp,
                    lineHeight = 38.sp,
                    fontFamily = FontFamily.Cursive
                )
            )
            Text(
                text = date,
                color = Color(0xFFB8A69A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Cursive
            )
        }
    }
}

@Composable
fun SensorGrid(
    light: Int,
    sound: Int,
    hasAudioPermission: Boolean,
    isLightSupported: Boolean,
    isGyroSupported: Boolean,
    activityState: String,
    isScreenOn: Boolean,
    voicePitch: Float,
    voiceTone: String,
    onRequestPermission: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SensorCard(
                modifier = Modifier.weight(1f).height(160.dp),
                icon = Icons.Default.LightMode,
                cardBg = Color(0xFFFFF3E0),
                iconBg = Color(0xFFFF9800).copy(alpha = 0.15f),
                iconColor = Color(0xFFFF9800),
                value = if (isLightSupported) light.toString() else "不支持",
                label = "光照指数",
                progressValue = if (isLightSupported) (light.coerceAtMost(10000) / 10000f) else 0.3f,
                progressBg = Color(0xFFFF9800).copy(alpha = 0.15f),
                accentColor = Color(0xFFFF9800),
                smallValue = true
            )
            SensorCard(
                modifier = Modifier.weight(1f).height(160.dp),
                icon = Icons.Default.SettingsInputAntenna,
                cardBg = Color(0xFFFCE4EC),
                iconBg = Color(0xFFE91E63).copy(alpha = 0.15f),
                iconColor = Color(0xFFE91E63),
                value = if (hasAudioPermission) sound.toString() else "未授权",
                label = "环境分贝",
                showPermissionButton = !hasAudioPermission,
                onPermissionClick = onRequestPermission,
                progressValue = if (hasAudioPermission) (sound.coerceIn(0, 100) / 100f) else 0.3f,
                progressBg = Color(0xFFE91E63).copy(alpha = 0.15f),
                accentColor = Color(0xFFE91E63)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val pitchLabel = when {
                voicePitch <= 0f -> "未检测"
                voicePitch < 120f -> "低沉"
                voicePitch < 250f -> "自然"
                else -> "兴奋"
            }
            SensorCard(
                modifier = Modifier.weight(1f).height(160.dp),
                icon = Icons.Default.Speaker,
                cardBg = Color(0xFFEDE7F6),
                iconBg = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                iconColor = Color(0xFF7C4DFF),
                value = if (voicePitch > 0) "${voicePitch.toInt()}Hz" else "说话检测中...",
                label = if (voiceTone != "未知") "音调${pitchLabel} · ${voiceTone}" else "语音语气",
                progressValue = if (voicePitch > 0) ((voicePitch - 60f) / 400f).coerceIn(0f, 1f) else 0f,
                progressBg = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                accentColor = Color(0xFF7C4DFF),
                smallValue = voicePitch <= 0f
            )
            SensorCard(
                modifier = Modifier.weight(1f).height(160.dp),
                icon = Icons.Default.MonitorHeart,
                cardBg = Color(0xFFE0F2F1),
                iconBg = Color(0xFF009688).copy(alpha = 0.15f),
                iconColor = Color(0xFF009688),
                value = activityState,
                label = "活动状态",
                progressBg = Color(0xFF009688).copy(alpha = 0.15f),
                accentColor = Color(0xFF009688)
            )
        }
    }
}

@Composable
fun SensorCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    cardBg: Color,
    accentColor: Color,
    iconBg: Color,
    iconColor: Color,
    value: String,
    label: String,
    showPermissionButton: Boolean = false,
    onPermissionClick: () -> Unit = {},
    progressValue: Float = 0f,
    progressBg: Color = Color.White.copy(alpha = 0.2f),
    smallValue: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(35.dp),
        color = cardBg,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = iconBg
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (showPermissionButton) {
                Button(
                    onClick = onPermissionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "点击授权",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor
                    )
                }
            } else {
                val valueSize = if (smallValue) 13.sp else 18.sp
                Text(text = value, fontSize = valueSize, fontWeight = FontWeight.Black, color = accentColor)
            }

            // Progress bar for numeric values
            if (progressValue > 0f && !showPermissionButton) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(progressBg, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressValue.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(accentColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    )
                }
            }

            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        color = Slate300,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp
    )
}

@Composable
fun LogCard(record: MoodRecord) {
    val timeStr = remember(record.timestamp) {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINESE)
        sdf.format(java.util.Date(record.timestamp))
    }
    val energyPercent = (record.energyScore * 100).toInt()
    val energyColor = when {
        record.energyScore > 0.8f -> Color(0xFF22C55E)
        record.energyScore > 0.5f -> Color(0xFF4F46E5)
        record.energyScore > 0.3f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Slate50
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = record.emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                Text(text = record.description, fontSize = 9.sp, color = Slate400)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Energy bar
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(6.dp)
                            .background(Slate200, RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(record.energyScore)
                                .fillMaxHeight()
                                .background(energyColor, RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "$energyPercent%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = energyColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                energyColor.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = record.activityState, fontSize = 8.sp, color = energyColor, fontWeight = FontWeight.Bold)
                    }
                    if (record.voiceTone.isNotEmpty() && record.voiceTone != "未知") {
                        Spacer(modifier = Modifier.width(6.dp))
                        val toneColor = when (record.voiceTone) {
                            "平稳" -> Color(0xFF22C55E)
                            "起伏" -> Color(0xFFF59E0B)
                            "紧张" -> Color(0xFFEF4444)
                            else -> Slate400
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    toneColor.copy(alpha = 0.15f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = record.voiceTone, fontSize = 8.sp, color = toneColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                text = timeStr,
                fontSize = 10.sp,
                color = Slate300,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
fun DisclaimerTip() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "温馨提示",
            color = Slate300,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "情绪分析数据由传感器算法生成，仅供参考，不作为专业诊断依据。",
            color = Slate300.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

@Composable
fun Modifier.drawBehindBorder(color: Color, strokeWidth: androidx.compose.ui.unit.Dp): Modifier {
    return this.drawBehind {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(0f, size.height),
            strokeWidth = strokeWidth.toPx()
        )
    }
}
