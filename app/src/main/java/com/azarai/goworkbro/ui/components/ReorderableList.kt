package com.azarai.goworkbro.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Long-press drag reordering for LazyColumn. Items swap when the dragged
 * card's midpoint crosses a neighbour's midpoint; the commit happens on
 * release via [ReorderState.onMove] (which updates sort orders).
 */
class ReorderState internal constructor(val listState: LazyListState) {
    var draggingKey by mutableStateOf<Any?>(null)
        internal set
    var dragOffset by mutableFloatStateOf(0f)
        internal set
    var onMove: ((fromKey: Any, toKey: Any) -> Unit)? = null

    internal fun start(key: Any) {
        draggingKey = key
        dragOffset = 0f
    }

    internal fun drag(deltaY: Float) {
        if (draggingKey == null) return
        dragOffset += deltaY
        val draggedInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggingKey } ?: return
        val draggedCenter = draggedInfo.offset + dragOffset + draggedInfo.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != draggingKey &&
                item.offset < draggedCenter && draggedCenter < item.offset + item.size
        } ?: return
        val callback = onMove ?: return
        // Keep the card under the finger after the data order flips.
        dragOffset += draggedInfo.offset - target.offset
        callback(draggingKey!!, target.key)
    }

    internal fun end() {
        draggingKey = null
        dragOffset = 0f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState): ReorderState =
    remember { ReorderState(listState) }

/** Attach to the drag-handle icon of a reorderable row. */
fun Modifier.dragHandle(state: ReorderState, key: Any): Modifier =
    pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }

/** Attach to the row itself so it follows the finger while dragging. */
fun Modifier.dragItem(state: ReorderState?, key: Any): Modifier =
    if (state == null) {
        this
    } else {
        graphicsLayer {
            if (state.draggingKey == key) {
                translationY = state.dragOffset
            }
        }.zIndex(if (state.draggingKey == key) 1f else 0f)
    }
