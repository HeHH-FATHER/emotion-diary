package com.example.sensordiary.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensordiary.ui.theme.*
import com.example.sensordiary.viewmodel.MainViewModel

@Composable
fun AnalysisScreen(viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBackground)
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            item {
                AnalysisHeader(
                    onShare = { viewModel.copyAnalysisToClipboard() },
                    onClearCache = { viewModel.toggleClearConfirmDialog(true) }
                )
                Spacer(modifier = Modifier.height(40.dp))

                // Quick Stats Row
                QuickStatsRow(viewModel.moodRecords.size, viewModel.energyTrendData)
                Spacer(modifier = Modifier.height(32.dp))

                MonthGridCard(viewModel.monthEmojis, viewModel.energyTrendData)
                Spacer(modifier = Modifier.height(32.dp))
                TrendChartCard(viewModel.energyTrendData)
            }
        }

        if (viewModel.showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleClearConfirmDialog(false) },
                title = {
                    Text(
                        text = "清理本地数据",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Slate800
                    )
                },
                text = {
                    Text(
                        text = "是否确认清理所有检测记录？此操作不可撤销。",
                        fontSize = 14.sp,
                        color = Slate400
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.clearAllRecords() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("确认清理", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleClearConfirmDialog(false) }) {
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
fun QuickStatsRow(recordCount: Int, trendData: List<Int>) {
    val avgEnergy = if (trendData.any { it > 0 }) {
        trendData.filter { it > 0 }.average().toInt()
    } else 0
    val bestEnergy = if (trendData.any { it > 0 }) trendData.max() else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuickStatCard(
            modifier = Modifier.weight(1f),
            value = recordCount.toString(),
            label = "本周记录",
            icon = "📊"
        )
        QuickStatCard(
            modifier = Modifier.weight(1f),
            value = "$avgEnergy%",
            label = "平均能量",
            icon = "⚡"
        )
        QuickStatCard(
            modifier = Modifier.weight(1f),
            value = "$bestEnergy%",
            label = "最佳状态",
            icon = "🌟"
        )
    }
}

@Composable
fun QuickStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rose100),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Slate800)
            Text(text = label, fontSize = 8.sp, color = Slate400, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AnalysisHeader(onShare: () -> Unit, onClearCache: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "深度洞察",
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
                text = "多维传感器情绪报告",
                color = Color(0xFFB8A69A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Cursive
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onClearCache,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Cache",
                    tint = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Rose400,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TrendChartCard(data: List<Int>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rose100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 16.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Rose400, Rose100)
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "本周能量趋势图",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate800,
                    letterSpacing = (-0.5).sp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            EnergyAreaChart(data)
            Spacer(modifier = Modifier.height(16.dp))
            val dayLabels = remember {
                val sdf = java.text.SimpleDateFormat("E", java.util.Locale.CHINESE)
                val cal = java.util.Calendar.getInstance()
                List(7) { i ->
                    val c = java.util.Calendar.getInstance()
                    c.add(java.util.Calendar.DAY_OF_YEAR, -(6 - i))
                    sdf.format(c.time)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEach { day ->
                    Text(
                        text = day,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400
                    )
                }
            }
        }
    }
}

@Composable
fun EnergyAreaChart(data: List<Int>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = width / (data.size - 1)
        val maxVal = 100f

        // Build smooth path
        val path = Path()
        val fillPath = Path()

        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value / maxVal * height)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevY = height - (data[index - 1] / maxVal * height)
                val controlX1 = prevX + stepX / 2
                val controlY1 = prevY
                val controlX2 = prevX + stepX / 2
                val controlY2 = y
                path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
            }
        }

        // Fill area under curve with gradient
        val lastX = (data.size - 1) * stepX
        val gradientPath = Path().apply {
            addPath(fillPath)
            lineTo(lastX, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = gradientPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Rose400.copy(alpha = 0.3f),
                    Rose400.copy(alpha = 0.02f)
                )
            )
        )

        // Draw line
        drawPath(
            path = path,
            color = Rose400,
            style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )

        // Draw dots at data points
        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value / maxVal * height)
            drawCircle(
                color = if (value > 0) Color.White else Color.Transparent,
                radius = 4.dp.toPx()
            )
            drawCircle(
                color = if (value > 0) Rose400 else Color.Transparent,
                radius = 3.dp.toPx()
            )
        }
    }
}

@Composable
fun MonthGridCard(emojis: List<String>, trendData: List<Int> = emptyList()) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Rose100),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "情绪日历",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Slate300,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            val chunks = emojis.chunked(7)
            val energyColors = remember(trendData) {
                trendData.map { score ->
                    when {
                        score > 80 -> Color(0xFF22C55E).copy(alpha = 0.3f)
                        score > 50 -> Color(0xFFFB7185).copy(alpha = 0.3f)
                        score > 30 -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                        score > 0 -> Color(0xFFEF4444).copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }
                }
            }

            chunks.forEachIndexed { rowIndex, chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    chunk.forEachIndexed { colIndex, emoji ->
                        val day = rowIndex * 7 + colIndex + 1
                        val dayIndex = (colIndex + rowIndex * 7)
                        val bgColor = if (dayIndex < energyColors.size) energyColors[dayIndex] else Color.Transparent
                        if (day <= 31) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(32.dp)
                            ) {
                                Text(
                                    text = day.toString(),
                                    fontSize = 7.sp,
                                    color = Slate300,
                                    modifier = Modifier.offset(y = 4.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(bgColor, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji.ifEmpty { "•" },
                                        fontSize = 14.sp,
                                        color = if (emoji.isEmpty()) Slate200 else Slate800
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.width(32.dp))
                        }
                    }
                }
                if (rowIndex < chunks.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
