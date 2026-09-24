package com.azarai.goworkbro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Mini 7-day bar chart. [data]: (label, total) pairs, oldest first. */
@Composable
fun WeekBars(
    data: List<Pair<String, Int>>,
    goal: Int,
    modifier: Modifier = Modifier,
) {
    val extras = LocalForestExtras.current
    val maxVal = maxOf(goal, data.maxOfOrNull { it.second } ?: 1, 1)
    Row(
        modifier
            .fillMaxWidth()
            .height(92.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEachIndexed { index, (label, value) ->
            val isLast = index == data.lastIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .height(56.dp)
                        .fillMaxWidth(0.62f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val progress by animateFloatAsState(
                        targetValue = (value.toFloat() / maxVal).coerceIn(0.04f, 1f),
                        animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy, stiffness = 180f),
                        label = "weekBar$index",
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    value <= 0 -> extras.divider
                                    value >= goal -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            ),
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLast) MaterialTheme.colorScheme.primary else extras.textSecondary,
                )
            }
        }
    }
}
