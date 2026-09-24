package com.azarai.goworkbro.ui.routine

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.IconBubble
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.components.TimeLineChart
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** 起床 / 睡觉打卡页：打卡按钮、修改时间、统计 + 7 天折线图。 */
@Composable
fun RoutineScreen(wake: Boolean, onBack: () -> Unit) {
    val vm: RoutineViewModel = viewModel(
        key = if (wake) "routine_wake" else "routine_sleep",
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("no application")
                RoutineViewModel(app, wake)
            }
        },
    )
    val state by vm.state.collectAsState()
    val extras = LocalForestExtras.current
    val tick = rememberTick()

    var editCurrent by remember { mutableStateOf(false) }
    var missedSleep by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CuteTopBar(title = if (wake) "起床打卡" else "睡觉打卡", onBack = onBack)

        // hero
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBubble(
                if (wake) "sun" else "moon",
                extras.pastels[if (wake) 6 else 4],
                92.dp,
                bob = true,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (state.currentTime != null) "今天 ${state.currentTime}" else if (wake) "今天还没起床打卡" else "今天还没睡觉打卡",
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.currentTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = { tick(); vm.checkIn { missedSleep = true } },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    "打卡",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 44.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            TextButton(onClick = { editCurrent = true }) {
                Text("修改时间", style = MaterialTheme.typography.bodySmall)
            }
        }

        // stats
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("连续", "${state.streak} 天", Modifier.weight(1f))
            StatCard(if (wake) "平均起床" else "平均入睡", state.avgLabel, Modifier.weight(1f))
            StatCard("累计", "${state.totalDays} 天", Modifier.weight(1f))
        }

        // line chart
        Spacer(Modifier.height(14.dp))
        Text(
            "最近 7 天",
            style = MaterialTheme.typography.titleSmall,
            color = extras.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Box(Modifier.padding(horizontal = 18.dp)) {
            TimeLineChart(
                points = state.points,
                avgMinutes = state.avgMinutes,
                wake = wake,
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    if (editCurrent) {
        TimeEditDialog(
            initial = state.currentTime,
            wake = wake,
            onDismiss = { editCurrent = false },
            onConfirm = { h, m ->
                vm.setTime(h, m)
                editCurrent = false
            },
            onClear = {
                vm.clearCurrentDay()
                editCurrent = false
            },
        )
    }

    if (missedSleep) {
        MissedSleepDialog(
            onDismiss = { missedSleep = false },
            onConfirm = { sleepAt ->
                vm.checkInWakeWithSleep(sleepAt)
                missedSleep = false
            },
            onAllNighter = {
                vm.checkInWakeAllNighter()
                missedSleep = false
            },
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val extras = LocalForestExtras.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = extras.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, extras.cardBorder),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = extras.textSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditDialog(
    initial: String?,
    wake: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    val parts = initial?.split(":")
    val pickerState = rememberTimePickerState(
        initialHour = parts?.getOrNull(0)?.toIntOrNull() ?: if (wake) 8 else 23,
        initialMinute = parts?.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) { Text("确定") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onClear) { Text("清除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = { Text(if (wake) "几点起床的？" else "几点睡的？") },
        text = { TimePicker(state = pickerState) },
        containerColor = LocalForestExtras.current.card,
    )
}
