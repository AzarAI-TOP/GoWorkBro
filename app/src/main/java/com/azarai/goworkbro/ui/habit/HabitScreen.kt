package com.azarai.goworkbro.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.ui.components.AddActionButton
import com.azarai.goworkbro.ui.components.ColorDots
import com.azarai.goworkbro.ui.components.CuteItemCard
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.EmptyHint
import com.azarai.goworkbro.ui.components.IconPicker
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Habit detail page: two-column grid of cute cards, tap = +1. */
@Composable
fun HabitScreen(onBack: () -> Unit) {
    val vm: HabitViewModel = viewModel()
    val habits by vm.habits.collectAsState()
    var editing by remember { mutableStateOf<EditingHabit?>(null) }

    Column(Modifier.fillMaxSize()) {
        CuteTopBar(
            title = "习惯",
            onBack = onBack,
            action = { AddActionButton(onClick = { editing = EditingHabit(null) }) },
        )
        if (habits.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyHint(icon = "sprout", text = "还没有习惯\n点右上角 + 种下一颗小苗吧")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(habits, key = { it.id }) { habit ->
                    CuteItemCard(
                        title = habit.title,
                        icon = habit.icon,
                        colorIndex = habit.colorIndex,
                        done = habit.isCompleted,
                        status = "${habit.currentCount}/${habit.targetCount} ${habit.unit}" +
                            if (habit.isCompleted) " ✓" else "",
                        onAction = { vm.checkIn(habit) },
                        onEdit = { editing = EditingHabit(habit) },
                    )
                }
            }
        }
    }

    editing?.let { state ->
        HabitEditDialog(
            initial = state.existing,
            onDismiss = { editing = null },
            onSave = { title, target, unit, icon, color ->
                if (state.existing == null) vm.add(title, target, unit, icon, color)
                else vm.update(state.existing, title, target, unit, icon, color)
                editing = null
            },
            onDelete = {
                state.existing?.let { vm.delete(it) }
                editing = null
            },
        )
    }
}

private data class EditingHabit(val existing: Habit?)

@Composable
private fun HabitEditDialog(
    initial: Habit?,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var target by remember { mutableStateOf((initial?.targetCount ?: 1).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "次") }
    var icon by remember { mutableStateOf(initial?.icon ?: "sprout") }
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: 1) }
    val extras = LocalForestExtras.current
    val targetNum = target.trim().toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(title, targetNum ?: 1, unit, icon, colorIndex) },
                enabled = title.isNotBlank() && targetNum != null && targetNum > 0,
            ) { Text("保存") }
        },
        dismissButton = {
            if (initial != null) {
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = { Text(if (initial == null) "新习惯" else "编辑习惯") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(20) },
                    singleLine = true,
                    label = { Text("名称") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.filter { c -> c.isDigit() }.take(2) },
                        singleLine = true,
                        label = { Text("每日目标") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(4) },
                        singleLine = true,
                        label = { Text("单位") },
                        modifier = Modifier.weight(1f),
                    )
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
