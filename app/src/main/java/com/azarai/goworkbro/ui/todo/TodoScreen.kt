package com.azarai.goworkbro.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.ui.components.AddActionButton
import com.azarai.goworkbro.ui.components.ColorDots
import com.azarai.goworkbro.ui.components.ConfirmDeleteDialog
import com.azarai.goworkbro.ui.components.CartoonIcons
import com.azarai.goworkbro.ui.components.CuteItemCard
import com.azarai.goworkbro.ui.components.IconBubble
import com.azarai.goworkbro.ui.components.randomColorIndex
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.EmptyHint
import com.azarai.goworkbro.ui.components.IconPicker
import com.azarai.goworkbro.ui.components.rememberDragSort
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.theme.LocalForestExtras

private val DURATION_PRESETS = listOf(10, 20, 30, 45)
private const val CUSTOM = -1

/** Todo detail page: two-column grid of cute cards. */
@Composable
fun TodoScreen(
    onBack: () -> Unit,
    onOpenTimer: (Todo) -> Unit,
    autoOpenAdd: Boolean = false,
    onAutoAddConsumed: () -> Unit = {},
) {
    val vm: TodoViewModel = viewModel()
    val todoState by vm.state.collectAsState()
    var editing by remember { mutableStateOf<EditingTodo?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Long-press drag sorting. Only the open list is sortable — finished work
    // keeps its completion order.
    val sort = rememberDragSort(
        gridState = gridState,
        items = todoState.undone,
        idOf = { it.id },
        onReorder = vm::reorder,
        haptics = rememberTick(),
    )
    // Completing an item pushes it down into "已完成" and the keyed grid follows
    // it there. Snap back to the top instead: the point of finishing something
    // is picking the next one.
    val backToTop = { scope.launch { gridState.animateScrollToItem(0) } }
    // One action for open AND finished cards: a finished timed task re-opens
    // and starts a NEW round (that is what "再来一轮" means), a plain one
    // just flips back to open.
    val act: (Todo) -> Unit = { todo ->
        when {
            todo.isDone && todo.timing != TimingType.NONE -> {
                vm.setDone(todo, false)
                onOpenTimer(todo.copy(isDone = false, doneDate = ""))
            }
            todo.timing == TimingType.NONE -> {
                vm.toggle(todo)
                if (!todo.isDone) backToTop()
            }
            else -> onOpenTimer(todo)
        }
    }

    // arrived via the "新建待办" launcher shortcut: open the add sheet once
    LaunchedEffect(autoOpenAdd) {
        if (autoOpenAdd) {
            editing = EditingTodo(null)
            onAutoAddConsumed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        CuteTopBar(
            title = "待办",
            onBack = onBack,
            action = { AddActionButton(onClick = { editing = EditingTodo(null) }) },
        )
        if (todoState.undone.isEmpty() && todoState.doneToday.isEmpty() &&
            todoState.olderDoneTotal == 0
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyHint(icon = "scroll", text = "还没有待办\n点右上角 + 添加一个吧")
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sort.items, key = { it.id }) { todo ->
                    TodoCard(
                        todo = todo,
                        modifier = Modifier
                            .then(if (sort.draggingKey == todo.id) Modifier else Modifier.animateItem())
                            .then(sort.cardModifier(todo.id)),
                        gestureModifier = sort.gestureModifier(todo.id),
                        onAction = { act(todo) },
                        onEdit = { editing = EditingTodo(todo) },
                    )
                }
                // finished work is history: today's stays, older folds away
                if (todoState.doneToday.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            "已完成 ${todoState.doneToday.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalForestExtras.current.textSecondary,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(todoState.doneToday, key = { it.id }) { todo ->
                        TodoCard(
                            todo = todo,
                            onAction = { act(todo) },
                            onEdit = { editing = EditingTodo(todo) },
                        )
                    }
                }
                // previous days' completions: one folded row, nothing deleted
                if (todoState.olderDoneTotal > 0) {
                    item(span = { GridItemSpan(2) }) {
                        TextButton(
                            onClick = { vm.toggleOlder() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (todoState.showOlder) {
                                    "收起更早完成"
                                } else {
                                    "更早完成 ${todoState.olderDoneTotal} 条"
                                },
                            )
                        }
                    }
                }
                if (todoState.showOlder) {
                    items(todoState.olderDone, key = { it.id }) { todo ->
                        TodoCard(
                            todo = todo,
                            onAction = { act(todo) },
                            onEdit = { editing = EditingTodo(todo) },
                        )
                    }
                    if (todoState.olderDoneTotal > todoState.olderDone.size) {
                        item(span = { GridItemSpan(2) }) {
                            TextButton(
                                onClick = { vm.expandDone() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("展开更多（还有 ${todoState.olderDoneTotal - todoState.olderDone.size} 条）")
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { state ->
        TodoEditSheet(
            initial = state.existing,
            onDismiss = { editing = null },
            onSave = { title, icon, color, timing, duration ->
                if (state.existing == null) vm.add(title, icon, color, timing, duration)
                else vm.update(state.existing, title, icon, color, timing, duration)
                editing = null
            },
            onToggleDone = {
                state.existing?.let {
                    vm.setDone(it, !it.isDone)
                    if (!it.isDone) backToTop()
                }
                editing = null
            },
            onDelete = {
                state.existing?.let { vm.delete(it) }
                editing = null
            },
        )
    }
}

@Composable
private fun TodoCard(
    todo: Todo,
    onAction: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier,
) {
    CuteItemCard(
        title = todo.title,
        icon = todo.icon,
        colorIndex = todo.colorIndex,
        done = todo.isDone,
        status = when {
            todo.isDone -> "已完成"
            todo.timing == TimingType.FORWARD -> "▶ 正向计时"
            todo.timing == TimingType.COUNTDOWN -> "▶ 倒计时 ${todo.durationMinutes}min"
            else -> "待完成"
        },
        strikeTitle = true,
        onAction = onAction,
        onMore = onEdit,
        modifier = modifier,
        gestureModifier = gestureModifier,
    )
}

private data class EditingTodo(val existing: Todo?)

/**
 * Create / edit sheet. A bottom sheet instead of the old AlertDialog: the
 * dialog had grown to hold 24 icons + 8 colours on top of the form, which
 * pushed the actions off-screen and clipped the last colour. The appearance
 * block now folds away behind one summary row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoEditSheet(
    initial: Todo?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, TimingType, Int) -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "scroll") }
    val extras = LocalForestExtras.current
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: randomColorIndex()) }
    var timing by remember { mutableStateOf(initial?.timing ?: TimingType.NONE) }
    // preset id (10/20/30/45) or CUSTOM
    var durationSel by remember {
        mutableStateOf(if (initial == null) 20 else (initial.durationMinutes.takeIf { it in DURATION_PRESETS } ?: CUSTOM))
    }
    var customDuration by remember {
        mutableStateOf((initial?.durationMinutes?.takeIf { it !in DURATION_PRESETS } ?: 25).toString())
    }
    var showAppearance by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val durationMin = if (durationSel == CUSTOM) customDuration.trim().toIntOrNull() ?: 25 else durationSel

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = extras.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (initial == null) "新待办" else "编辑待办",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(30) },
                singleLine = true,
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("计时方式", style = MaterialTheme.typography.labelMedium, color = extras.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TimingType.NONE to "不计时",
                    TimingType.FORWARD to "正向计时",
                    TimingType.COUNTDOWN to "倒计时",
                ).forEach { (type, label) ->
                    Chip(
                        text = label,
                        selected = timing == type,
                        onClick = { timing = type },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (timing == TimingType.COUNTDOWN) {
                Text("倒计时时长", style = MaterialTheme.typography.labelMedium, color = extras.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATION_PRESETS.forEach { preset ->
                        Chip(
                            text = "$preset",
                            selected = durationSel == preset,
                            onClick = { durationSel = preset },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Chip(
                        text = "自定义",
                        selected = durationSel == CUSTOM,
                        onClick = { durationSel = CUSTOM },
                        modifier = Modifier.weight(1.3f),
                    )
                }
                if (durationSel == CUSTOM) {
                    OutlinedTextField(
                        value = customDuration,
                        onValueChange = { customDuration = it.filter { c -> c.isDigit() }.take(3) },
                        singleLine = true,
                        label = { Text("自定义时长") },
                        suffix = { Text("分钟") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // appearance: folded away — it is set once and then never touched
            Surface(
                onClick = { showAppearance = !showAppearance },
                shape = RoundedCornerShape(16.dp),
                color = extras.inputFill,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(icon, extras.pastels[colorIndex % extras.pastels.size], 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "外观",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${CartoonIcons.labelOf(icon) ?: icon} · 第 ${colorIndex + 1} 色",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.textSecondary,
                        )
                    }
                    Icon(
                        if (showAppearance) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (showAppearance) "收起图标配色" else "展开图标配色",
                        tint = extras.textSecondary,
                    )
                }
            }
            if (showAppearance) {
                IconPicker(selected = icon, onSelect = { icon = it }, scrollable = false)
                Text("选个颜色", style = MaterialTheme.typography.labelMedium, color = extras.textSecondary)
                ColorDots(selected = colorIndex, onSelect = { colorIndex = it })
            }

            // primary action, always reachable without scrolling past the grid
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Surface(
                    onClick = { onSave(title, icon, colorIndex, timing, durationMin) },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.6f),
                ) {
                    Text(
                        "保存",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    )
                }
            }
            if (initial != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onToggleDone) {
                        Text(if (initial.isDone) "标记未完成" else "标记完成")
                    }
                    // finished todos are history and are not deletable
                    if (!initial.isDone) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "删除这条待办？",
            text = "「${initial?.title.orEmpty()}」会被删掉，无法恢复。",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

/** Small pill chip used for timing / duration selection. */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else LocalForestExtras.current.inputFill,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalForestExtras.current.cardBorder,
        ),
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else LocalForestExtras.current.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }
}
