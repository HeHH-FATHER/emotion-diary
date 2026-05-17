package com.example.sensordiary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensordiary.model.MoodOption
import com.example.sensordiary.ui.theme.*

@Composable
fun ResultModal(
    mood: MoodOption?,
    energyScore: Float,
    lightValue: Int,
    dbValue: Int,
    shakeValue: Float,
    activityState: String,
    voicePitch: Float,
    voiceTone: String,
    voiceContent: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onCopyResult: () -> Unit
) {
    if (mood == null) return

    val energyPercent = (energyScore * 100).toInt()
    val energyColor = when {
        energyScore > 0.8f -> Color(0xFF22C55E)
        energyScore > 0.5f -> Color(0xFF4F46E5)
        energyScore > 0.3f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    val pitchLabel = when {
        voicePitch <= 0f -> "未检测到"
        voicePitch < 120f -> "低沉"
        voicePitch < 250f -> "自然"
        else -> "兴奋"
    }

    val pitchColor = when (pitchLabel) {
        "低沉" -> Color(0xFF0D9488)
        "自然" -> Color(0xFF22C55E)
        "兴奋" -> Color(0xFFF97316)
        else -> Slate400
    }

    val toneColor = when (voiceTone) {
        "平稳" -> Color(0xFF22C55E)
        "起伏" -> Color(0xFFF59E0B)
        "紧张" -> Color(0xFFEF4444)
        else -> Slate400
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(45.dp),
            color = Color.White,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = mood.emoji, fontSize = 72.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = mood.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate800
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = mood.description,
                    fontSize = 11.sp,
                    color = Slate400,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Energy Score Bar
                EnergyBar(energyScore, energyColor, energyPercent)

                Spacer(modifier = Modifier.height(24.dp))

                // Voice Analysis Card (New!)
                VoiceAnalysisCard(
                    pitch = voicePitch,
                    pitchLabel = pitchLabel,
                    pitchColor = pitchColor,
                    tone = voiceTone,
                    toneColor = toneColor,
                    content = voiceContent
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Sensor Data Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorDataCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LightMode,
                        iconColor = Amber500,
                        value = lightValue.toString(),
                        label = "光照(lux)"
                    )
                    SensorDataCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.SettingsInputAntenna,
                        iconColor = Rose400,
                        value = dbValue.toString(),
                        label = "分贝(dB)"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SensorDataCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Shuffle,
                        iconColor = Teal600,
                        value = String.format("%.1f Hz", shakeValue),
                        label = "抖动频率"
                    )
                    SensorDataCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MonitorHeart,
                        iconColor = Color(0xFFF97316),
                        value = activityState,
                        label = "活动状态"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onCopyResult,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate50),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "复制", color = Slate400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate50),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(text = "忽略", color = Slate400, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Rose400),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "保存",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceAnalysisCard(
    pitch: Float,
    pitchLabel: String,
    pitchColor: Color,
    tone: String,
    toneColor: Color,
    content: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Rose50,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rose100.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speaker,
                    contentDescription = null,
                    tint = Rose400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "语音分析",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Rose400
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VoiceTag(
                    modifier = Modifier.weight(1f),
                    label = "音调",
                    value = pitchLabel,
                    color = pitchColor,
                    subLabel = if (pitch > 0) "${pitch.toInt()}Hz" else ""
                )
                VoiceTag(
                    modifier = Modifier.weight(1f),
                    label = "语气",
                    value = tone,
                    color = toneColor,
                    subLabel = ""
                )
            }
            if (content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = content,
                        fontSize = 10.sp,
                        color = Rose400,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceTag(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color,
    subLabel: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 8.sp, color = Slate400, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
        if (subLabel.isNotEmpty()) {
            Text(text = subLabel, fontSize = 8.sp, color = Slate400)
        }
    }
}

@Composable
fun EnergyBar(score: Float, color: Color, percent: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "能量值 $percent%",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Slate50, RoundedCornerShape(4.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(score)
                    .fillMaxHeight()
            ) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun SensorDataCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Slate50
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
            Text(text = label, fontSize = 8.sp, color = Slate400, fontWeight = FontWeight.Bold)
        }
    }
}
