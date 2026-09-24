package com.azarai.goworkbro.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.ui.components.AddActionButton
import com.azarai.goworkbro.ui.components.ColorDots
import com.azarai.goworkbro.ui.components.CuteItemCard
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.EmptyHint
import com.azarai.goworkbro.ui.components.IconPicker
import com.azarai.goworkbro.ui.theme.LocalForestExtras

private val DURATION_PRESETS = listOf(10, 20, 30, 45)
private const val CUSTOM = -1

/** Todo detail page: two-column grid of cute cards. */
@Composable
fun TodoScreen(onBack: () -> Unit, onOpenTimer: (Todo) -> Unit) {
    val vm: TodoViewModel = viewModel()
    val todos by vm.todos.collectAsState()
    var editing by remember { mutableStateOf<EditingTodo?>(null) }

    Column(Modifier.fillMaxSize()) {
        CuteTopBar(
            title = "待办",
            onBack = onBack,
            action = { AddActionButton(onClick = { editing = EditingTodo(null) }) },
        )
        if (todos.isEmpty()) {
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
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(todos, key = { it.id }) { todo ->
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
                        onAction = {
                            if (todo.timing == TimingType.NONE) vm.toggle(todo) else onOpenTimer(todo)
                        },
                        onEdit = { editing = EditingTodo(todo) },
                    )
                }
            }
        }
    }

    editing?.let { state ->
        TodoEditDialog(
            initial = state.existing,
            onDismiss = { editing = null },
            onSave = { title, icon, color, timing, duration ->
                if (state.existing == null) vm.add(title, icon, color, timing, duration)
                else vm.update(state.existing, title, icon, color, timing, duration)
                editing = null
            },
            onToggleDone = {
                state.existing?.let { vm.setDone(it, !it.isDone) }
                editing = null
            },
            onDelete = {
                state.existing?.let { vm.delete(it) }
                editing = null
            },
        )
    }
}

private data class EditingTodo(val existing: Todo?)

@Composable
private fun TodoEditDialog(
    initial: Todo?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, TimingType, Int) -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "scroll") }
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: 0) }
    var timing by remember { mutableStateOf(initial?.timing ?: TimingType.NONE) }
    // preset id (10/20/30/45) or CUSTOM
    var durationSel by remember {
        mutableStateOf(if (initial == null) 20 else (initial.durationMinutes.takeIf { it in DURATION_PRESETS } ?: CUSTOM))
    }
    var customDuration by remember {
        mutableStateOf((initial?.durationMinutes?.takeIf { it !in DURATION_PRESETS } ?: 25).toString())
    }
    val durationMin = if (durationSel == CUSTOM) customDuration.trim().toIntOrNull() ?: 25 else durationSel
    val extras = LocalForestExtras.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(title, icon, colorIndex, timing, durationMin) }, enabled = title.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = {
            if (initial != null) {
                Row {
                    TextButton(onClick = onToggleDone) {
                        Text(if (initial.isDone) "标记未完成" else "标记完成")
                    }
                    // finished todos are history and are not deletable
                    if (!initial.isDone) {
                        TextButton(onClick = onDelete) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = { Text(if (initial == null) "新待办" else "编辑待办") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(30) },
                    singleLine = true,
                    label = { Text("标题") },
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
                        )
                    }
                }
                Text("选个图标", style = MaterialTheme.typography.labelMedium, color = extras.textSecondary)
                IconPicker(selected = icon, onSelect = { icon = it })
                Text("选个颜色", style = MaterialTheme.typography.labelMedium, color = extras.textSecondary)
                ColorDots(selected = colorIndex, onSelect = { colorIndex = it })
            }
        },
        containerColor = extras.card,
    )
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
