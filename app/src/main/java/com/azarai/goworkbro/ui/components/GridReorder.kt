package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.azarai.goworkbro.core.util.Ordering

/**
 * Drag sorting for one list/tab: what the grid renders, plus the long-press
 * reordering on top of it.
 *
 * While the finger is down the list is reordered LOCALLY so the grid follows
 * without touching the database on every swap; [onReorder] is called once on
 * drop, and the local order then lives on until the database emits the same
 * order (or a different id set, which means this drag went stale).
 *
 * [items] must be the grid's first cells — the lazy index of a hovered cell is
 * used directly as an index into the list (see [GridReorder]).
 */
@Composable
fun <T> rememberDragSort(
    gridState: LazyGridState,
    items: List<T>,
    idOf: (T) -> String,
    onReorder: (ids: List<String>) -> Unit,
    haptics: () -> Unit,
): DragSort<T> {
    var dragged by remember { mutableStateOf<List<T>?>(null) }
    val liveIds = remember(items) { items.mapTo(HashSet(), idOf) }
    // rows that vanished (completed / deleted elsewhere) drop out of the drag
    val shown = remember(items, dragged) { (dragged ?: items).filter { idOf(it) in liveIds } }
    val reorder = rememberGridReorder(
        state = gridState,
        itemCount = shown.size,
        onMove = { from, to -> dragged = Ordering.moved(shown, from, to) },
        onDrop = {
            val local = dragged
            if (local != null) {
                onReorder(local.map(idOf))
                // a drag whose rows changed under it will never be confirmed by
                // a database emission — release the override right here
                if (local.mapTo(HashSet(), idOf) != items.mapTo(HashSet(), idOf)) dragged = null
            }
        },
    )
    // release the override as soon as the stored order matches what the user dragged
    LaunchedEffect(items) {
        val local = dragged ?: return@LaunchedEffect
        if (items.map(idOf) == local.map(idOf)) dragged = null
    }
    return remember(shown, reorder, haptics) { DragSort(shown, reorder, haptics) }
}

/** Rows to render plus the modifiers/markers the drag needs. */
class DragSort<T> internal constructor(
    /** The caller's items in the order the user currently sees them. */
    val items: List<T>,
    private val reorder: GridReorder,
    private val haptics: () -> Unit,
) {
    /** Lazy key of the card under the finger, null when idle. */
    val draggingKey: Any? get() = reorder.draggingKey

    /**
     * Visual half of the drag (lift + follow the finger): goes on the card
     * ROOT, next to `animateItem()`.
     */
    fun cardModifier(key: Any): Modifier = reorder.cardModifier(key)

    /**
     * Gesture half: must be applied INSIDE the card's click handler, because
     * pointer events reach inner nodes first — that is what lets the drag
     * swallow the release so lifting a card does not also tap it.
     */
    fun gestureModifier(key: Any): Modifier = reorder.gestureModifier(key, haptics)
}

/**
 * Launcher-style long-press drag sorting for a LazyVerticalGrid.
 *
 * The reorderable section MUST be the grid's FIRST [itemCount] items. A
 * hovered cell's lazy index is then used directly as an index into the list
 * the caller is dragging. Prefer [rememberDragSort], which owns that list.
 */
@Composable
fun rememberGridReorder(
    state: LazyGridState,
    itemCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): GridReorder {
    val reorder = remember(state) { GridReorder(state) }
    reorder.itemCount = itemCount
    reorder.onMove = onMove
    reorder.onDrop = onDrop
    return reorder
}

/** Drag state holder returned by [rememberGridReorder]. */
class GridReorder internal constructor(private val gridState: LazyGridState) {

    /** The lazy key of the card currently being dragged, null when idle. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** Finger delta since the last swap; drawn, never persisted. */
    private var offset by mutableStateOf(Offset.Zero)

    internal var itemCount: Int = 0
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
    internal var onDrop: () -> Unit = {}

    /**
     * Visual half of one card's drag state (z-order, lift, finger tracking).
     * [key] must be the card's lazy key (a stable id, never the index — the
     * index changes as soon as a swap happens).
     */
    fun cardModifier(key: Any): Modifier = Modifier
        .zIndex(if (draggingKey == key) 1f else 0f)
        .graphicsLayer {
            val dragging = draggingKey == key
            translationX = if (dragging) offset.x else 0f
            translationY = if (dragging) offset.y else 0f
            // lifted-card look: a soft shadow while it follows the finger
            shadowElevation = if (dragging) 14.dp.toPx() else 0f
            shape = RoundedCornerShape(22.dp)
            clip = dragging
        }

    /**
     * Gesture half. Apply it INSIDE the card's click handler: pointer events
     * reach inner nodes first, so this can swallow the release of a long press
     * that never moved — otherwise lifting a card would also tap it.
     */
    fun gestureModifier(key: Any, onDragStart: () -> Unit = {}): Modifier =
        Modifier.pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                // Consume the press: without this the card's click handler also
                // fires on release, so a long press meant as "pick up" would
                // tap the card (complete the todo / check in the habit).
                longPress.consume()
                onDragStart()
                draggingKey = key
                offset = Offset.Zero
                // a manual loop instead of drag(): the RELEASE must be consumed
                // too, or the card's own tap handler still fires on the way out
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                    // read the delta BEFORE consuming: positionChange() reports
                    // zero on an already-consumed change
                    val delta = change.positionChangeIgnoreConsumed()
                    change.consume()
                    if (change.changedToUpIgnoreConsumed()) break
                    offset += delta
                    swapUnderFinger(key)
                }
                endDrag()
            }
        }

    private fun endDrag() {
        val moved = draggingKey != null
        draggingKey = null
        offset = Offset.Zero
        if (moved) onDrop()
    }

    /** Swap with whatever card the dragged center now covers, then rebase the
     *  finger offset onto the dragged card's new cell so it stays put visually. */
    private fun swapUnderFinger(key: Any) {
        val layout = gridState.layoutInfo
        val current = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val center = Offset(
            current.offset.x + current.size.width / 2f + offset.x,
            current.offset.y + current.size.height / 2f + offset.y,
        )
        autoScroll(layout, center)
        val target = layout.visibleItemsInfo.firstOrNull { info ->
            info.index in 0 until itemCount && info.index != current.index &&
                center.x >= info.offset.x && center.x <= info.offset.x + info.size.width &&
                center.y >= info.offset.y && center.y <= info.offset.y + info.size.height
        } ?: return
        onMove(current.index, target.index)
        offset = Offset(
            offset.x - (target.offset.x - current.offset.x),
            offset.y - (target.offset.y - current.offset.y),
        )
    }

    /** Drag near the top / bottom edge and the grid keeps scrolling. */
    private fun autoScroll(layout: LazyGridLayoutInfo, center: Offset) {
        val edge = (layout.viewportEndOffset - layout.viewportStartOffset) * 0.12f
        when {
            center.y < layout.viewportStartOffset + edge -> gridState.dispatchRawDelta(-12f)
            center.y > layout.viewportEndOffset - edge -> gridState.dispatchRawDelta(12f)
        }
    }
}
