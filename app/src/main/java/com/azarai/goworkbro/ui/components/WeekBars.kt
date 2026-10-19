package com.azarai.goworkbro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/**
 * Mini 7-day bar chart. [data]: (label, total) pairs, oldest first.
 *
 * Reads as a chart even on an empty week: every bar carries its value, the
 * goal is drawn as a dashed line, and a day with no record shows a dot
 * instead of the 1px sliver a 4%-height bar used to render as.
 */
@Composable
fun WeekBars(
    data: List<Pair<String, Int>>,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val extras = LocalForestExtras.current
    if (data.none { it.second > 0 }) {
        Box(
            modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "近 7 天还没有记录",
                style = MaterialTheme.typography.bodySmall,
                color = extras.textSecondary,
            )
        }
        return
    }

    // 12% headroom above the tallest bar / the goal, so the dashed goal line
    // sits inside the plot instead of hugging the top edge like a border
    val maxVal = maxOf(goal, data.maxOfOrNull { it.second } ?: 1, 1) * 1.12f
    val goalFrac = (goal / maxVal).coerceIn(0f, 1f)
    val valueRowHeight = 18.dp
    val barBand = 56.dp
    val goalLine = extras.textSecondary
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "近 7 天：" +
                    data.joinToString("，") { "${it.first} ${it.second}" }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(valueRowHeight + barBand)) {
            val band = maxHeight - valueRowHeight
            Canvas(Modifier.fillMaxSize()) {
                val y = (valueRowHeight + band * (1f - goalFrac)).toPx()
                drawLine(
                    color = goalLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                data.forEachIndexed { index, (_, value) ->
                    val isLast = index == data.lastIndex
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (value > 0) "$value" else "-",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLast) primary else extras.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.height(valueRowHeight),
                        )
                        Box(
                            Modifier.weight(1f).fillMaxWidth(0.62f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (value <= 0) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(extras.divider),
                                )
                            } else {
                                val progress by animateFloatAsState(
                                    targetValue = (value / maxVal).coerceIn(0.05f, 1f),
                                    animationSpec = spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        stiffness = 180f,
                                    ),
                                    label = "weekBar$index",
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(progress)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (value >= goal) secondary else primary),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEachIndexed { index, (label, _) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == data.lastIndex) primary else extras.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
