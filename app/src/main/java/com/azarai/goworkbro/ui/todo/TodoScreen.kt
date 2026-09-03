package com.azarai.goworkbro.ui.todo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.ui.OverlayViewModel
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.components.AppCard
import com.azarai.goworkbro.ui.components.CircleButton
import com.azarai.goworkbro.ui.components.InfoChip
import com.azarai.goworkbro.ui.components.dragHandle
import com.azarai.goworkbro.ui.components.dragItem
import com.azarai.goworkbro.ui.components.rememberReorderState
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.theme.ChartColors
import com.azarai.goworkbro.ui.theme.HabitBlue
import com.azarai.goworkbro.ui.theme.TodoOrange

private fun keyOf(item: CombinedItem): String = when (item) {
    is CombinedItem.TodoItem -> "todo:${item.todo.id}"
    is CombinedItem.HabitItem -> "habit:${item.habit.id}"
}

/** Editing target passed between the options sheet and the dialogs. */
private sealed interface EditTarget {
    data object NewTodo : EditTarget
    data object NewHabit : EditTarget
    data class TodoTarget(val todo: Todo) : EditTarget
    data class HabitTarget(val habit: Habit) : EditTarget
}

@Composable
fun TodoRoute(overlays: OverlayViewModel, vm: TodoViewModel = viewModel()) {
    val todos by vm.todos.collectAsState()
    val habits by vm.habits.collectAsState()
    val colors = AppTheme.colors

    var showAddOverlay by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<EditTarget?>(null) }
    var optionsTarget by remember { mutableStateOf<EditTarget?>(null) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val customUnits by vm.customUnits.collectAsState()

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState)
    val haptics = LocalHapticFeedback.current

    reorder.onMove = { fromKey, toKey ->
        val current = buildCombinedList(todos, habits)
        val from = current.indexOfFirst { keyOf(it) == fromKey }
        val to = current.indexOfFirst { keyOf(it) == toKey }
        if (from >= 0 && to >= 0) {
            // Swaps arrive one neighbour at a time, matching v1's onReorder
            // values, so `to` is used directly as the new index.
            mapReorder(todos, habits, from, to)?.let { mapping ->
                if (mapping.isHabit) {
                    vm.reorderHabits(mapping.oldIndex, mapping.newIndex)
                } else {
                    vm.reorderTodos(mapping.oldIndex, mapping.newIndex)
                }
            }
        }
    }

    fun onTodoTap(todo: Todo) {
        when {
            todo.isCompleted -> vm.toggleComplete(todo)
            todo.timing == TimingType.NONE -> vm.toggleComplete(todo)
            else -> overlays.open(com.azarai.goworkbro.ui.Overlay.Timer(todo.id))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // App bar: title + counters
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = appString(R.string.tab_todo),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                val habitDoneCount = habits.count { it.isCompleted }
                val counterText = buildString {
                    append(
                        appString(
                            R.string.completed_counter,
                            todos.count { it.isCompleted },
                            todos.size,
                        ),
                    )
                    if (habits.isNotEmpty()) {
                        append("  ·  ")
                        append(appString(R.string.habit_counter, habitDoneCount, habits.size))
                    }
                }
                Text(
                    text = counterText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                )
            }

            val activeItems = buildCombinedList(todos, habits)
                .filter { item ->
                    when (item) {
                        is CombinedItem.TodoItem -> !item.todo.isCompleted
                        is CombinedItem.HabitItem -> !item.habit.isCompleted
                    }
                }
            val completedItems = buildCombinedList(todos, habits)
                .filter { item ->
                    when (item) {
                        is CombinedItem.TodoItem -> item.todo.isCompleted
                        is CombinedItem.HabitItem -> item.habit.isCompleted
                    }
                }

            if (activeItems.isEmpty() && completedItems.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(activeItems, key = ::keyOf) { item ->
                        when (item) {
                            is CombinedItem.TodoItem -> TodoCard(
                                todo = item.todo,
                                reorder = reorder,
                                onToggle = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onTodoTap(item.todo) },
                                onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); optionsTarget = EditTarget.TodoTarget(item.todo) },
                            )
                            is CombinedItem.HabitItem -> HabitCard(
                                habit = item.habit,
                                reorder = reorder,
                                onIncrement = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.incrementHabit(item.habit) },
                                onDecrement = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.decrementHabit(item.habit) },
                                onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); optionsTarget = EditTarget.HabitTarget(item.habit) },
                            )
                        }
                    }
                    if (completedItems.isNotEmpty()) {
                        item(key = "completed-divider") {
                            CompletedDivider(
                                count = completedItems.size,
                                expanded = completedExpanded,
                                onToggle = { completedExpanded = !completedExpanded },
                            )
                        }
                        if (completedExpanded) {
                            items(completedItems, key = ::keyOf) { item ->
                                when (item) {
                                    is CombinedItem.TodoItem -> TodoCard(
                                        todo = item.todo,
                                        reorder = null,
                                        onToggle = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onTodoTap(item.todo) },
                                        onLongPress = { optionsTarget = EditTarget.TodoTarget(item.todo) },
                                    )
                                    is CombinedItem.HabitItem -> HabitCard(
                                        habit = item.habit,
                                        reorder = null,
                                        onIncrement = { vm.incrementHabit(item.habit) },
                                        onDecrement = { vm.decrementHabit(item.habit) },
                                        onLongPress = { optionsTarget = EditTarget.HabitTarget(item.habit) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        ) {
            FloatingActionButton(
                onClick = { showAddOverlay = true },
                containerColor = colors.primary,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Add, contentDescription = appString(R.string.add))
            }
        }

        if (showAddOverlay) {
            AddOverlay(
                onTodo = { showAddOverlay = false; editTarget = EditTarget.NewTodo },
                onHabit = { showAddOverlay = false; editTarget = EditTarget.NewHabit },
                onDismiss = { showAddOverlay = false },
            )
        }
    }

    // Options sheet (edit / delete)
    optionsTarget?.let { target ->
        OptionsSheet(
            onEdit = {
                editTarget = target
                optionsTarget = null
            },
            onDelete = {
                deleteTarget = target
                optionsTarget = null
            },
            onDismiss = { optionsTarget = null },
        )
    }

    // Add / edit dialogs
    editTarget?.let { target ->
        when (target) {
            is EditTarget.NewTodo -> TodoEditDialog(
                initial = null,
                onDismiss = { editTarget = null },
                onSave = { updated, isNew ->
                    if (isNew) vm.addTodo(updated.title, updated.timingType, updated.durationMinutes, updated.keepTomorrow)
                    else vm.updateTodo(updated)
                    editTarget = null
                },
            )
            is EditTarget.NewHabit -> HabitEditDialog(
                initial = null,
                savedUnits = customUnits,
                onPersistCustomUnit = { vm.addCustomUnit(it) },
                onDismiss = { editTarget = null },
                onSave = { updated, isNew ->
                    if (isNew) vm.addHabit(updated.title, updated.targetCount, updated.unit)
                    else vm.updateHabit(updated)
                    editTarget = null
                },
            )
            is EditTarget.TodoTarget -> TodoEditDialog(
                initial = target.todo,
                onDismiss = { editTarget = null },
                onSave = { updated, isNew ->
                    if (isNew) vm.addTodo(updated.title, updated.timingType, updated.durationMinutes, updated.keepTomorrow)
                    else vm.updateTodo(updated)
                    editTarget = null
                },
            )
            is EditTarget.HabitTarget -> HabitEditDialog(
                initial = target.habit,
                savedUnits = customUnits,
                onPersistCustomUnit = { vm.addCustomUnit(it) },
                onDismiss = { editTarget = null },
                onSave = { updated, isNew ->
                    if (isNew) vm.addHabit(updated.title, updated.targetCount, updated.unit)
                    else vm.updateHabit(updated)
                    editTarget = null
                },
            )
        }
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        val title = when (target) {
            is EditTarget.TodoTarget -> target.todo.title
            is EditTarget.HabitTarget -> target.habit.title
            else -> ""
        }
        ConfirmDeleteDialog(
            title = title,
            onConfirm = {
                when (target) {
                    is EditTarget.TodoTarget -> vm.deleteTodo(target.todo.id)
                    is EditTarget.HabitTarget -> vm.deleteHabit(target.habit.id)
                    else -> Unit
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun EmptyState() {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircleOutline,
                contentDescription = null,
                tint = colors.divider,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                appString(R.string.no_todos),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                appString(R.string.no_todos_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
            )
        }
    }
}

/** "已完成 · N" pill divider; tapping expands/collapses the done zone. */
@Composable
private fun CompletedDivider(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.primary.copy(alpha = 0.22f)),
        )
        Row(
            Modifier
                .padding(horizontal = 12.dp)
                .background(colors.primary.copy(alpha = 0.08f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DoneAll,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${appString(R.string.completed)} · $count",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.primary.copy(alpha = 0.22f)),
        )
    }
}

/** v1 TodoCard: tap toggles; timing chips; drag handle when active. */
@Composable
fun TodoCard(
    todo: Todo,
    reorder: com.azarai.goworkbro.ui.components.ReorderState?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = AppTheme.colors
    val isDone = todo.isCompleted
    val timingColor = when (todo.timing) {
        TimingType.FORWARD -> HabitBlue
        TimingType.BACKWARD -> colors.primary
        TimingType.NONE -> colors.textFaint
    }
    val key = "todo:${todo.id}"

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (reorder?.draggingKey == key) {
                    Modifier.shadow(6.dp, CircleShape, clip = false)
                } else {
                    Modifier
                },
            )
            .dragItem(reorder, key),
        onClick = onToggle,
        onLongClick = onLongPress,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reorder != null) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = colors.textFaint,
                    modifier = Modifier
                        .size(22.dp)
                        .dragHandle(reorder, key),
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(2.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDone) colors.textFaint else colors.textSecondary,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(
                        text = appString(
                            when (todo.timing) {
                                TimingType.FORWARD -> R.string.timing_forward
                                TimingType.BACKWARD -> R.string.timing_backward
                                TimingType.NONE -> R.string.timing_none
                            },
                        ),
                        color = timingColor,
                    )
                    if (todo.timing == TimingType.BACKWARD) {
                        InfoChip("${todo.durationMinutes}min", colors.primary)
                    }
                    if (todo.keepTomorrow && !isDone) {
                        InfoChip(appString(R.string.keep_tomorrow), ChartColors[2])
                    }
                }
            }
        }
    }
}

/** v1 HabitCard: progress bar, +/- buttons, repeat icon. */
@Composable
fun HabitCard(
    habit: Habit,
    reorder: com.azarai.goworkbro.ui.components.ReorderState?,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = AppTheme.colors
    val done = habit.isCompleted
    val canDecrement = habit.currentCount > 0
    val key = "habit:${habit.id}"
    val progress = if (habit.targetCount <= 0) 0f else {
        (habit.currentCount.toFloat() / habit.targetCount).coerceIn(0f, 1f)
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (reorder?.draggingKey == key) {
                    Modifier.shadow(6.dp, CircleShape, clip = false)
                } else {
                    Modifier
                },
            )
            .dragItem(reorder, key),
        onClick = null,
        onLongClick = onLongPress,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reorder != null) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = colors.textFaint,
                    modifier = Modifier
                        .size(22.dp)
                        .dragHandle(reorder, key),
                )
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(26.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = if (done) colors.primary else colors.textFaint,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (done) colors.textFaint else colors.textSecondary,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        appString(R.string.habit_progress, habit.currentCount, habit.targetCount, habit.unit),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (done) colors.primary else colors.textFaint,
                    )
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = if (done) ChartColors[2] else colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.12f),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (habit.targetCount == 1) {
                CircleButton(
                    icon = Icons.Filled.Check,
                    color = if (done) ChartColors[2] else colors.primary,
                    contentDescription = appString(
                        if (done) R.string.habit_undo else R.string.habit_check,
                    ),
                ) { if (done) onDecrement() else onIncrement() }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircleButton(
                        icon = Icons.Filled.Add,
                        color = colors.primary,
                        enabled = !done,
                        contentDescription = appString(R.string.habit_inc),
                    ) { onIncrement() }
                    Spacer(Modifier.height(6.dp))
                    CircleButton(
                        icon = Icons.Filled.Remove,
                        color = colors.textFaint,
                        enabled = canDecrement,
                        contentDescription = appString(R.string.habit_dec),
                    ) { onDecrement() }
                }
            }
        }
    }
}

/** Full-screen dim overlay with two large circles (v1 _AddOverlay). */
@Composable
private fun AddOverlay(
    onTodo: () -> Unit,
    onHabit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(200)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
            .clickable(onClick = onDismiss),
    ) {
        Row(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
        ) {
            BigCircle(
                label = "TODO",
                color = TodoOrange,
                modifier = Modifier.weight(1f),
                scale = scale.value,
                onTap = onTodo,
            )
            BigCircle(
                label = "HABIT",
                color = HabitBlue,
                modifier = Modifier.weight(1f),
                scale = scale.value,
                onTap = onHabit,
            )
        }
    }
}

@Composable
private fun BigCircle(
    label: String,
    color: Color,
    modifier: Modifier,
    scale: Float,
    onTap: () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(16.dp, CircleShape, ambientColor = color.copy(alpha = 0.4f))
                .background(color, CircleShape)
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
        }
    }
}
