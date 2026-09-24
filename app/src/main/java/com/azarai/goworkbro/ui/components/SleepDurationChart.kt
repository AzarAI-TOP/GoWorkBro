package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** One night on the sleep-duration chart. [hours] null = no record. */
data class SleepPoint(val label: String, val hours: Float?)

/**
 * Hand-drawn 7-night sleep DURATION line chart (hours slept per night).
 * Missing nights break the line; the value is printed above each dot.
 */
@Composable
fun SleepDurationChart(points: List<SleepPoint>, modifier: Modifier = Modifier) {
    val extras = LocalForestExtras.current
    val line = MaterialTheme.colorScheme.secondary
    val strongLabel = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val leftPad = 44.dp.toPx()
        val rightPad = 14.dp.toPx()
        val topPad = 22.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val w = size.width
        val h = size.height
        val chartW = w - leftPad - rightPad
        val chartH = h - topPad - bottomPad

        val recorded = points.mapNotNull { it.hours }
        val yMax = maxOf(10f, ((recorded.maxOrNull() ?: 8f) + 1f).coerceAtMost(14f))
        val yAt = { hours: Float -> topPad + chartH * (1f - (hours / yMax).coerceIn(0f, 1f)) }
        val xAt = { i: Int -> leftPad + if (points.size <= 1) chartW / 2f else chartW * i / (points.size - 1) }

        val gridPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = with(density) { 10.sp.toPx() }
            color = extras.textSecondary.toArgb()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val xPaint = android.graphics.Paint(gridPaint).apply {
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val valuePaint = android.graphics.Paint(gridPaint).apply {
            color = line.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // grid: 0h / half / max
        listOf(0f, yMax / 2f, yMax).forEach { hours ->
            val y = yAt(hours)
            drawLine(extras.divider, Offset(leftPad, y), Offset(w - rightPad, y), 1.5f)
            drawContext.canvas.nativeCanvas.drawText("${hours.toInt()}h", leftPad - 8.dp.toPx(), y + 4.dp.toPx(), gridPaint)
        }

        // smooth line through consecutive recorded nights
        val pts = points.mapIndexed { i, p -> if (p.hours != null) i to p.hours else null }
            .filterNotNull()
        if (pts.size >= 2) {
            val path = Path()
            var prevX = 0f
            var prevY = 0f
            pts.forEachIndexed { si, (i, hours) ->
                val x = xAt(i)
                val y = yAt(hours)
                if (si == 0) path.moveTo(x, y) else {
                    val dx = (x - prevX) / 2f
                    path.cubicTo(prevX + dx, prevY, x - dx, y, x, y)
                }
                prevX = x
                prevY = y
            }
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }

        // dots + value labels
        points.forEachIndexed { i, p ->
            val hours = p.hours ?: return@forEachIndexed
            val x = xAt(i)
            val y = yAt(hours)
            drawCircle(line, 4.5.dp.toPx(), Offset(x, y))
            if (points.size <= 8) {
                val text = if (hours % 1f == 0f) "${hours.toInt()}h" else "%.1fh".format(hours)
                drawContext.canvas.nativeCanvas.drawText(text, x, y - 9.dp.toPx(), valuePaint)
            }
        }

        // x labels
        points.forEachIndexed { i, p ->
            xPaint.color = if (p.hours == null) extras.textSecondary.toArgb() else strongLabel.toArgb()
            drawContext.canvas.nativeCanvas.drawText(p.label, xAt(i), h - 6.dp.toPx(), xPaint)
        }
    }
}
