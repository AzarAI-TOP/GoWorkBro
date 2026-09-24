package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Cute medium-saturation palette that reads well on light and dark cards. */
val PiePalette = listOf(
    Color(0xFF7DBB6A),
    Color(0xFF7EC8E3),
    Color(0xFFF5B942),
    Color(0xFFFF9F9F),
    Color(0xFFB79CE8),
    Color(0xFFA97C50),
    Color(0xFF6FC7B5),
    Color(0xFFE8A33D),
)

data class PieSlice(val label: String, val minutes: Int, val colorIndex: Int)

/**
 * Donut chart of today's focus minutes per todo, with a legend underneath.
 * Slices are drawn with thick round-capped arcs so it stays cute at any size.
 */
@Composable
fun FocusPieChart(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val extras = LocalForestExtras.current
    val strongColor = MaterialTheme.colorScheme.onSurface
    val total = slices.sumOf { it.minutes }
    if (total <= 0) {
        EmptyHint(icon = "file", text = "今天还没有专注记录", modifier = modifier.fillMaxWidth())
        return
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val stroke = 30.dp.toPx()
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke,
                )
                var start = -90f
                slices.forEach { slice ->
                    val sweep = 360f * slice.minutes / total
                    drawArc(
                        color = PiePalette[slice.colorIndex % PiePalette.size],
                        startAngle = start + 1.2f,
                        sweepAngle = (sweep - 2.4f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    start += sweep
                }
                // centre label
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = extras.textSecondary.toArgb()
                    textSize = 12.sp.toPx()
                }
                val strong = android.graphics.Paint(paint).apply {
                    color = strongColor.toArgb()
                    textSize = 20.sp.toPx()
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.apply {
                    drawText("今日专注", size.width / 2f, size.height / 2f - 4.dp.toPx(), paint)
                    drawText(
                        Dates.formatMinutesHuman(total),
                        size.width / 2f,
                        size.height / 2f + 20.dp.toPx(),
                        strong,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(PiePalette[slice.colorIndex % PiePalette.size]),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        "${slice.minutes} 分钟 · ${Math.round(slice.minutes * 100f / total)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.textSecondary,
                    )
                }
            }
        }
    }
}
