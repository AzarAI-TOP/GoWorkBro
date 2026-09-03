package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.ui.theme.AppTheme

/** Pie chart with legend (Today tab, source distribution). */
@Composable
fun PieChart(
    slices: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
) {
    val colors = AppTheme.colors
    val palette = colors.chart
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    if (slices.isEmpty() || slices.sumOf { it.second } == 0) {
        Text(
            text = "—",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textFaint,
            modifier = modifier.padding(vertical = 24.dp),
        )
        return
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val total = slices.sumOf { it.second }.toFloat()
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .pointerInput(slices) {
                    detectTapGestures { offset: Offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val radius = minOf(size.width, size.height) / 2f
                        selectedIndex = if (dx * dx + dy * dy <= radius * radius) {
                            var angle = Math.toDegrees(
                                Math.atan2(dy.toDouble(), dx.toDouble()),
                            )
                            // 0° at 12 o'clock, clockwise
                            angle = (angle + 90.0 + 360.0) % 360.0
                            var acc = 0.0
                            var hit: Int? = null
                            slices.forEachIndexed { index, (_, value) ->
                                val sweep = value / total * 360.0
                                if (hit == null && angle >= acc && angle < acc + sweep) hit = index
                                acc += sweep
                            }
                            hit
                        } else {
                            null
                        }
                    }
                },
        ) {
            val radius = size.minDimension / 2f
            var startAngle = -90f
            slices.forEachIndexed { index, (_, value) ->
                val sweep = value / total * 360f
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
                startAngle += sweep
            }
            // Donut hole
            drawCircle(
                color = colors.card,
                radius = radius * 0.55f,
                center = center,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val label = if (selectedIndex != null && selectedIndex!! < slices.size) {
                slices[selectedIndex!!]
            } else {
                null
            }
            if (label != null) {
                LegendDot(palette[selectedIndex!! % palette.size], "${label.first} · ${label.second}")
            } else {
                slices.take(6).forEachIndexed { index, (name, value) ->
                    LegendDot(palette[index % palette.size], "$name $value")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            maxLines = 1,
        )
    }
}

/** 7-day bar chart (Today tab focus hours / sleep minutes). */
@Composable
fun WeekBarChart(
    values: List<Float>,
    labels: List<String>,
    valueLabel: (Float) -> String,
    highlightLast: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var tappedIndex by remember { mutableStateOf<Int?>(null) }
    val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(0.001f)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(values) {
                    detectTapGestures { offset: Offset ->
                        val barWidth = size.width / values.size
                        tappedIndex = (offset.x / barWidth).toInt().coerceIn(0, values.size - 1)
                    }
                },
        ) {
            val barWidth = size.width / values.size
            val gapRatio = 0.3f
            values.forEachIndexed { index, value ->
                val barHeight = (value / maxValue) * (size.height * 0.85f)
                val isToday = highlightLast && index == values.lastIndex
                drawRoundRect(
                    color = if (isToday) colors.primary else colors.primary.copy(alpha = 0.25f),
                    topLeft = Offset(
                        index * barWidth + barWidth * gapRatio / 2,
                        size.height - barHeight,
                    ),
                    size = Size(barWidth * (1 - gapRatio), barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    if (tappedIndex == index && values[index] > 0f) {
                        Text(
                            valueLabel(values[index]),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                            maxLines = 1,
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (highlightLast && index == labels.lastIndex) colors.primary else colors.textFaint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 7-day line chart with dashed average line (sleep trends). */
@Composable
fun TrendLineChart(
    values: List<Double?>,
    labels: List<String>,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val valid = values.filterNotNull()
    if (valid.isEmpty()) {
        Text(
            "—",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textFaint,
            modifier = modifier.padding(vertical = 24.dp),
        )
        return
    }
    var tappedIndex by remember { mutableStateOf<Int?>(null) }
    val min = valid.min()
    val max = valid.max()
    val range = (max - min).coerceAtLeast(0.5)
    val average = valid.average()

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(values) {
                    detectTapGestures { offset: Offset ->
                        val step = if (values.size > 1) size.width / (values.size - 1) else size.width
                        tappedIndex = ((offset.x / step).toInt() + 1).coerceIn(0, values.size - 1)
                    }
                },
        ) {
            fun yOf(value: Double): Float {
                return (size.height * 0.9f - ((value - min) / range).toFloat() * size.height * 0.75f).toFloat()
            }
            fun xOf(index: Int): Float {
                val step = if (values.size > 1) size.width / (values.size - 1) else size.width / 2
                return index * step
            }

            // Average dashed line
            drawLine(
                color = colors.primary.copy(alpha = 0.6f),
                start = Offset(0f, yOf(average)),
                end = Offset(size.width, yOf(average)),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )

            val path = Path()
            var started = false
            values.forEachIndexed { index, value ->
                if (value == null) {
                    started = false
                    return@forEachIndexed
                }
                val point = Offset(xOf(index), yOf(value))
                if (!started) {
                    path.moveTo(point.x, point.y)
                    started = true
                } else {
                    path.lineTo(point.x, point.y)
                }
            }
            drawPath(
                path = path,
                color = colors.primary,
                style = Stroke(width = 4f, cap = StrokeCap.Round),
            )
            values.forEachIndexed { index, value ->
                if (value != null) {
                    drawCircle(
                        color = colors.primary,
                        radius = if (tappedIndex == index) 10f else 6f,
                        center = Offset(xOf(index), yOf(value)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    val tapped = tappedIndex == index && values.getOrNull(index) != null
                    if (tapped) {
                        Text(
                            valueFormatter(values[index]!!),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                            maxLines = 1,
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
