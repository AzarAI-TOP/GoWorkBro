package com.azarai.goworkbro.ui.fitness

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextAlign
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.GoalHero
import com.azarai.goworkbro.ui.components.LogEntryRow
import com.azarai.goworkbro.ui.components.NumberInputDialog
import com.azarai.goworkbro.ui.components.SectionLabel
import com.azarai.goworkbro.ui.components.WeekBars
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Fitness page: the stopwatch (minutes can only be earned by timing),
 *  the daily goal, today's entries and the last 7 days. */
@Composable
fun FitnessScreen(onBack: () -> Unit) {
    val vm: FitnessViewModel = viewModel()
    val state by vm.state.collectAsState()
    val stopwatch by vm.stopwatch.collectAsState()
    val elapsedMs by vm.stopwatchMs.collectAsState()
    val tick = rememberTick()

    var editGoal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CuteTopBar(title = "健身", onBack = onBack)

        GoalHero(
            total = state.todayTotal,
            goal = state.goal,
            unit = "分钟",
            icon = "dumbbell",
            pastelIndex = 5,
            doneText = if (state.todayTotal >= state.goal) {
                "今日目标达成啦 🎉"
            } else {
                "还差 ${state.goal - state.todayTotal} 分钟"
            },
            onEditGoal = { editGoal = true },
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("运动计时")
        StopwatchCard(
            elapsedMs = elapsedMs,
            running = stopwatch.startedAt != null,
            started = stopwatch.accMs > 0 || stopwatch.startedAt != null,
            onToggle = { tick(); vm.toggleStopwatch() },
            onFinish = { tick(); vm.finishStopwatch() },
        )

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
                    "今天还没有运动记录，计时满 1 分钟才会入账哦",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalForestExtras.current.textSecondary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            state.todayLogs.reversed().forEach { log ->
                LogEntryRow(
                    text = "${log.loggedAt}  运动 ${log.minutes} 分钟",
                    onDelete = { vm.delete(log) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("近 7 天")
        Box(Modifier.padding(horizontal = 22.dp)) {
            WeekBars(data = state.week, goal = state.goal)
        }
        Spacer(Modifier.height(28.dp))
    }

    if (editGoal) {
        NumberInputDialog(
            title = "每日运动目标",
            initialValue = state.goal.toString(),
            unit = "分钟",
            onDismiss = { editGoal = false },
            onConfirm = { m -> editGoal = false; vm.setGoal(m) },
        )
    }
}

/** Rounded pill action button used by the stopwatch card. */
@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        border = border,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}

/**
 * The workout stopwatch: minutes are earned by timing, never typed in.
 * Idle shows one big start button; running/paused shows pause/resume + finish.
 */
@Composable
private fun StopwatchCard(
    elapsedMs: Long,
    running: Boolean,
    started: Boolean,
    onToggle: () -> Unit,
    onFinish: () -> Unit,
) {
    val extras = LocalForestExtras.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = extras.card,
        border = BorderStroke(1.5.dp, extras.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Dates.formatSeconds((elapsedMs / 1_000).toInt()),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    running -> "正在运动，去流汗吧 💪"
                    started -> "已暂停，随时继续"
                    else -> "点开始，动了才算数"
                },
                style = MaterialTheme.typography.bodySmall,
                color = extras.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            if (!started) {
                PillButton("开始运动", onToggle, Modifier.fillMaxWidth())
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PillButton(
                        if (running) "暂停" else "继续",
                        onToggle,
                        Modifier.weight(1f),
                        container = extras.inputFill,
                        content = MaterialTheme.colorScheme.onBackground,
                        border = BorderStroke(1.5.dp, extras.cardBorder),
                    )
                    PillButton("结束并记录", onFinish, Modifier.weight(1f))
                }
            }
        }
    }
}
