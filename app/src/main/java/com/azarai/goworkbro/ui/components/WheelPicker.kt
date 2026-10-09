package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.theme.LocalForestExtras
import java.time.LocalDateTime

private val ROW = 44.dp
private val VIEWPORT = 132.dp

/**
 * A snap wheel over [from]..[to] in 15-minute steps (the granularity the
 * sleep design settled on: design/sleep-log-preview.html). The centered row
 * is reported through [onSelected] on every settle — including the initial
 * value, so callers always have a valid pick. The wheel deliberately stops
 * at its ends instead of wrapping: an over-scrolled wheel must not silently
 * produce data from the wrong day.
 */
@Composable
fun TimeWheel(
    from: LocalDateTime,
    to: LocalDateTime,
    initial: LocalDateTime,
    onSelected: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalForestExtras.current
    val items = remember(from, to) {
        generateSequence(from) { it.plusMinutes(15) }
            .takeWhile { !it.isAfter(to) }
            .toList()
    }
    val clampedInitial = initial.coerceIn(from, to)
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = items.indexOfFirst { !it.isBefore(clampedInitial) }
            .let { if (it < 0) items.lastIndex else it },
    )
    val selected by remember {
        derivedStateOf {
            items[state.firstVisibleItemIndex.coerceIn(items.indices)]
        }
    }
    LaunchedEffect(selected) { onSelected(selected) }

    val divider = extras.divider
    Box(
        modifier
            .fillMaxWidth()
            .height(VIEWPORT)
            .background(extras.inputFill, RoundedCornerShape(14.dp))
            .drawBehind {
                // two hairlines framing the centered row, picker-style
                val y = (size.height - ROW.toPx()) / 2f
                drawLine(divider, Offset(12f, y), Offset(size.width - 12f, y), 1.5f)
                drawLine(divider, Offset(12f, y + ROW.toPx()), Offset(size.width - 12f, y + ROW.toPx()), 1.5f)
            },
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = (VIEWPORT - ROW) / 2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items.size) { i ->
                val at = items[i]
                val isCenter = at == selected
                Box(Modifier.height(ROW).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        Dates.hhmm(at),
                        fontSize = if (isCenter) 20.sp else 18.sp,
                        fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isCenter) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            extras.textSecondary
                        },
                    )
                }
            }
        }
    }
}
