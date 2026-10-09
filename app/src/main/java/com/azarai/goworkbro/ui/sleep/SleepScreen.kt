package com.azarai.goworkbro.ui.sleep

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.IconBubble
import com.azarai.goworkbro.ui.components.SleepDurationChart
import com.azarai.goworkbro.ui.components.TimeWheel
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.theme.LocalForestExtras
import java.time.LocalDate
import java.time.LocalDateTime

/** 睡眠记录页：hero + 统计 + 近 7 晚 + 两步滚轮记录弹窗。 */
@Composable
fun SleepScreen(
    onBack: () -> Unit,
    autoOpenRecord: Boolean = false,
    onAutoRecordConsumed: () -> Unit = {},
) {
    val vm: SleepViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("no application")
                SleepViewModel(app)
            }
        },
    )
    val state by vm.state.collectAsState()
    val extras = LocalForestExtras.current
    val tick = rememberTick()

    var sheetOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(autoOpenRecord) {
        if (autoOpenRecord) {
            sheetOpen = true
            onAutoRecordConsumed()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CuteTopBar(title = "睡眠记录", onBack = onBack)

        // hero
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBubble("moon", extras.pastels[4], 92.dp, bob = true)
            Spacer(Modifier.height(10.dp))
            Text(
                state.today?.let { SleepOps.formatDuration(it.minutes) } ?: "今天还没记录",
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.today != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.last?.let { r ->
                    "${SleepOps.dateLabel(r.sleep)} ${Dates.hhmm(r.sleep)} → " +
                        "${SleepOps.dateLabel(r.wake)} ${Dates.hhmm(r.wake)}"
                } ?: "醒来后记一晚：先滚睡眠时间，再滚起床时间",
                style = MaterialTheme.typography.bodySmall,
                color = extras.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = { tick(); sheetOpen = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    "记录睡眠",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 44.dp, vertical = 12.dp),
                )
            }
            if (state.last != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除最近一条", style = MaterialTheme.typography.bodySmall)
                }
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
            StatCard("平均时长", state.avgMinutes?.let { SleepOps.formatDuration(it) } ?: "--", Modifier.weight(1f))
            StatCard("平均入睡", state.avgSleepLabel, Modifier.weight(1f))
            StatCard("连续达标", "${state.streakDays} 晚", Modifier.weight(1f))
        }

        // chart
        Spacer(Modifier.height(14.dp))
        Text(
            "近 7 晚（虚线目标 7h）",
            style = MaterialTheme.typography.titleSmall,
            color = extras.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 18.dp)) {
            SleepDurationChart(points = state.points)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (sheetOpen) {
        SleepRecordSheet(
            onDismiss = { sheetOpen = false },
            onConfirm = { s, w ->
                tick()
                vm.record(s, w)
                sheetOpen = false
            },
        )
    }

    if (confirmDelete && state.last != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLast()
                    confirmDelete = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
            title = { Text("删除最近一条？") },
            text = {
                state.last?.let { r ->
                    Text(
                        "${SleepOps.dateLabel(r.sleep)} ${Dates.hhmm(r.sleep)} → " +
                            "${SleepOps.dateLabel(r.wake)} ${Dates.hhmm(r.wake)}（${SleepOps.formatDuration(r.minutes)}）",
                    )
                }
            },
            containerColor = LocalForestExtras.current.card,
        )
    }
}

/**
 * Two-step record flow (the settled design): wheel the bedtime, confirm,
 * wheel the wake time, confirm — the record is written once, complete.
 * Defaults: wake = now, sleep = 7h before now; the wake wheel starts at
 * sleep + 30min so a negative duration is structurally impossible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepRecordSheet(
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime, LocalDateTime) -> Unit,
) {
    val extras = LocalForestExtras.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }
    val now = remember { LocalDateTime.now() }
    val sleepFrom = today.minusDays(1).atTime(18, 0)
    val sleepTo = today.atTime(12, 0)
    val wakeMax = today.atTime(18, 0)

    var step by remember { mutableStateOf(1) }
    var sleepAt by remember {
        mutableStateOf(SleepOps.roundToStep(now.minusHours(7)).coerceIn(sleepFrom, sleepTo))
    }
    var wakeAt by remember { mutableStateOf(SleepOps.roundToStep(now).coerceIn(sleepAt, wakeMax)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = extras.card,
    ) {
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (step == 1) "第 1 步 · 睡眠时间" else "第 2 步 · 起床时间",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            if (step == 1) {
                TimeWheel(
                    from = sleepFrom,
                    to = sleepTo,
                    initial = sleepAt,
                    onSelected = { sleepAt = it },
                )
                LightDateLabel(sleepAt)
                ConfirmPill("确认睡眠时间") {
                    val defaultWake = SleepOps.roundToStep(now)
                        .coerceIn(sleepAt.plusMinutes(30), wakeMax)
                    wakeAt = defaultWake
                    step = 2
                }
            } else {
                TimeWheel(
                    from = sleepAt.plusMinutes(30),
                    to = wakeMax,
                    initial = wakeAt,
                    onSelected = { wakeAt = it },
                )
                LightDateLabel(wakeAt)
                ConfirmPill("确认起床时间") { onConfirm(sleepAt, wakeAt) }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 浅色居中日期：滚轮滚过 00:00 它自动跳到次日。 */
@Composable
private fun LightDateLabel(at: LocalDateTime) {
    Text(
        SleepOps.dateLabel(at),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalForestExtras.current.textSecondary,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConfirmPill(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
