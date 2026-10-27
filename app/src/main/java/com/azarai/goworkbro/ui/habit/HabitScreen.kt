package com.azarai.goworkbro.ui.habit

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
import com.azarai.goworkbro.core.db.Habit
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

/** Habit detail page: two-column grid of cute cards, tap = +1. */
@Composable
fun HabitScreen(onBack: () -> Unit) {
    val vm: HabitViewModel = viewModel()
    val habits by vm.habits.collectAsState()
    var editing by remember { mutableStateOf<EditingHabit?>(null) }
    val gridState = rememberLazyGridState()
    val sort = rememberDragSort(
        gridState = gridState,
        items = habits,
        idOf = { it.id },
        onReorder = vm::reorder,
        haptics = rememberTick(),
    )

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
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sort.items, key = { it.id }) { habit ->
                    CuteItemCard(
                        title = habit.title,
                        icon = habit.icon,
                        colorIndex = habit.colorIndex,
                        done = habit.isCompleted,
                        status = "${habit.currentCount}/${habit.targetCount} ${habit.unit}" +
                            if (habit.isCompleted) " ✓" else "",
                        onAction = { vm.checkIn(habit) },
                        onMore = { editing = EditingHabit(habit) },
                        modifier = Modifier
                            .then(if (sort.draggingKey == habit.id) Modifier else Modifier.animateItem())
                            .then(sort.cardModifier(habit.id)),
                        gestureModifier = sort.gestureModifier(habit.id),
                    )
                }
            }
        }
    }

    editing?.let { state ->
        HabitEditSheet(
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

/** Create / edit sheet — appearance folds away, delete asks first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditSheet(
    initial: Habit?,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var target by remember { mutableStateOf((initial?.targetCount ?: 1).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "次") }
    var icon by remember { mutableStateOf(initial?.icon ?: "sprout") }
    val extras = LocalForestExtras.current
    var colorIndex by remember { mutableStateOf(initial?.colorIndex ?: randomColorIndex()) }
    var showAppearance by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val targetNum = target.trim().toIntOrNull()

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
                if (initial == null) "新习惯" else "编辑习惯",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(20) },
                singleLine = true,
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
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

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Surface(
                    onClick = { onSave(title, targetNum ?: 1, unit, icon, colorIndex) },
                    enabled = title.isNotBlank() && targetNum != null && targetNum > 0,
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
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除这个习惯", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "删除这个习惯？",
            text = "「${initial?.title.orEmpty()}」和它的连续记录会一起消失，无法恢复。",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}
