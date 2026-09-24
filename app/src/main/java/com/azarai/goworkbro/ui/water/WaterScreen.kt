package com.azarai.goworkbro.ui.water

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.GoalHero
import com.azarai.goworkbro.ui.components.LogEntryRow
import com.azarai.goworkbro.ui.components.SectionLabel
import com.azarai.goworkbro.ui.components.NumberInputDialog
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.components.WeekBars
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** 喝水打卡页：目标、快捷杯、自定义、今日记录、近 7 天。 */
@Composable
fun WaterScreen(onBack: () -> Unit) {
    val vm: WaterViewModel = viewModel()
    val state by vm.state.collectAsState()
    val extras = LocalForestExtras.current
    val tick = rememberTick()

    var customDialog by remember { mutableStateOf(false) }
    var editCupIndex by remember { mutableStateOf<Int?>(null) }
    var editGoal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CuteTopBar(title = "喝水", onBack = onBack)

        GoalHero(
            total = state.todayTotal,
            goal = state.goal,
            unit = "ml",
            icon = "cup",
            pastelIndex = 2,
            doneText = if (state.todayTotal >= state.goal) {
                "今日目标达成啦 🎉"
            } else {
                "还差 ${state.goal - state.todayTotal} ml"
            },
            onEditGoal = { editGoal = true },
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("快捷杯子（长按修改）")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.cups.take(4).forEachIndexed { i, ml ->
                CupChip(
                    ml = ml,
                    onDrink = { tick(); vm.drink(ml) },
                    onEdit = { editCupIndex = i },
                    modifier = Modifier.weight(1f),
                )
            }
            Surface(
                onClick = { customDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "自定义",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("今日记录")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.todayLogs.isEmpty()) {
                Text(
                    "今天还没有喝水记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            state.todayLogs.reversed().forEach { log ->
                LogEntryRow(text = "${log.loggedAt}  喝了 ${log.ml} ml", onDelete = { vm.delete(log) })
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("近 7 天")
        Box(Modifier.padding(horizontal = 22.dp)) {
            WeekBars(data = state.week, goal = state.goal)
        }
        Spacer(Modifier.height(28.dp))
    }

    if (customDialog) {
        NumberInputDialog(
            title = "喝多少毫升？",
            initialValue = "",
            unit = "ml",
            onDismiss = { customDialog = false },
            onConfirm = { ml -> customDialog = false; tick(); vm.drink(ml) },
        )
    }
    editCupIndex?.let { index ->
        EditCupDialog(
            ml = state.cups.getOrNull(index) ?: return@let,
            onDismiss = { editCupIndex = null },
            onSave = { vm.updateCup(index, it); editCupIndex = null },
            onRemove = { vm.removeCup(index); editCupIndex = null },
        )
    }
    if (editGoal) {
        NumberInputDialog(
            title = "每日目标饮水量",
            initialValue = state.goal.toString(),
            unit = "ml",
            onDismiss = { editGoal = false },
            onConfirm = { ml -> editGoal = false; vm.setGoal(ml) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CupChip(ml: Int, onDrink: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.clip(RoundedCornerShape(18.dp)).combinedClickable(onClick = onDrink, onLongClick = onEdit),
    ) {
        Text(
            "${ml}ml",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun EditCupDialog(ml: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit, onRemove: () -> Unit) {
    var value by remember { mutableStateOf(ml.toString()) }
    val extras = LocalForestExtras.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                value.trim().toIntOrNull()?.let(onSave)
            }) { Text("保存") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onRemove) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        title = { Text("修改杯子容积") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { v -> value = v.filter { it.isDigit() }.take(5) },
                singleLine = true,
                suffix = { Text("ml") },
            )
        },
        containerColor = extras.card,
    )
}
