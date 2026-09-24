package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** One day on the routine line chart. [minutes] is wrapped (see RoutineViewModel). */
data class ChartPoint(
    val label: String,
    val minutes: Int?,
    val isCurrent: Boolean,
)

/**
 * Hand-drawn 7-day time-of-day line chart for wake / sleep.
 * Missing days break the line; today is highlighted; average is a dashed line.
 */
@Composable
fun TimeLineChart(
    points: List<ChartPoint>,
    avgMinutes: Int?,
    wake: Boolean,
    modifier: Modifier = Modifier,
) {
    val extras = LocalForestExtras.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current

    val gridColor = extras.divider
    val labelColor = extras.textSecondary.toArgb()
    val lineColor = primary
    val dotColor = primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        val leftPad = 46.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 14.dp.toPx()
        val bottomPad = 26.dp.toPx()
        val w = size.width
        val h = size.height
        val chartW = w - leftPad - rightPad
        val chartH = h - topPad - bottomPad

        // y range from data (wrapped minutes), with sane defaults
        val recorded = points.mapNotNull { it.minutes }
        val defaultMin = if (wake) 5 * 60f else 21 * 60f
        val defaultMax = if (wake) 12 * 60f else 27 * 60f
        var yMin: Float
        var yMax: Float
        if (recorded.size >= 2) {
            yMin = (recorded.min() - 40).toFloat()
            yMax = (recorded.max() + 40).toFloat()
            if (yMax - yMin < 180f) {
                val mid = (yMin + yMax) / 2f
                yMin = mid - 90f
                yMax = mid + 90f
            }
        } else {
            yMin = defaultMin
            yMax = defaultMax
        }
        avgMinutes?.let {
            yMin = minOf(yMin, it - 40f)
            yMax = maxOf(yMax, it + 40f)
        }

        val xAt = { i: Int ->
            leftPad + if (points.size <= 1) chartW / 2f else chartW * i.toFloat() / (points.size - 1)
        }
        val yAt = { m: Float -> topPad + chartH * (1f - ((m - yMin) / (yMax - yMin)).coerceIn(0f, 1f)) }

        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = with(density) { 10.sp.toPx() }
            color = labelColor
            textAlign = android.graphics.Paint.Align.RIGHT
            isFakeBoldText = false
        }
        val xPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = with(density) { 10.sp.toPx() }
            color = labelColor
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // grid lines + y labels
        for (g in 0..2) {
            val m = yMin + (yMax - yMin) * g / 2f
            val y = yAt(m)
            drawLine(gridColor, Offset(leftPad, y), Offset(w - rightPad, y), 1.5f)
            drawIntoCanvasText(formatMinutes(m), leftPad - 8.dp.toPx(), y + 4.dp.toPx(), labelPaint)
        }

        // average dashed line
        avgMinutes?.let {
            val y = yAt(it.toFloat())
            drawLine(
                color = secondary,
                start = Offset(leftPad, y),
                end = Offset(w - rightPad, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            val avgPaint = android.graphics.Paint(labelPaint).apply {
                color = secondary.toArgb()
                textAlign = android.graphics.Paint.Align.LEFT
            }
            drawIntoCanvasText("平均", leftPad + 4.dp.toPx(), y - 5.dp.toPx(), avgPaint)
        }

        // smooth line through consecutive recorded points
        val segments = points.mapIndexed { i, p -> if (p.minutes != null) i to p else null }.filterNotNull()
        if (segments.size >= 2) {
            val path = Path()
            var prevX = 0f
            var prevY = 0f
            segments.forEachIndexed { si, (i, p) ->
                val x = xAt(i)
                val y = yAt(p.minutes!!.toFloat())
                if (si == 0) {
                    path.moveTo(x, y)
                } else {
                    val dx = (x - prevX) / 2f
                    path.cubicTo(prevX + dx, prevY, x - dx, y, x, y)
                }
                prevX = x
                prevY = y
            }
            drawPath(
                path,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // dots
        points.forEachIndexed { i, p ->
            val minutes = p.minutes ?: return@forEachIndexed
            val x = xAt(i)
            val y = yAt(minutes.toFloat())
            if (p.isCurrent) {
                drawCircle(lineColor.copy(alpha = 0.25f), 9.dp.toPx(), Offset(x, y))
            }
            drawCircle(dotColor, if (p.isCurrent) 5.5.dp.toPx() else 4.5.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 1.8.dp.toPx(), Offset(x, y))
        }

        // x labels
        points.forEachIndexed { i, p ->
            xPaint.color = if (p.isCurrent) primary.toArgb() else labelColor
            xPaint.isFakeBoldText = p.isCurrent
            drawIntoCanvasText(p.label, xAt(i), h - 8.dp.toPx(), xPaint)
        }
    }
}

private fun formatMinutes(m: Float): String {
    val total = Math.round(m).toInt()
    val wrapped = ((total % 1440) + 1440) % 1440
    return "%d:%02d".format(wrapped / 60, wrapped % 60)
}

/** Thin indirection so the native-canvas import stays in one place. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoCanvasText(
    text: String,
    x: Float,
    y: Float,
    paint: android.graphics.Paint,
) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
