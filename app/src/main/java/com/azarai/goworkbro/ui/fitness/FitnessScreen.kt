package com.azarai.goworkbro.ui.fitness

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.GoalHero
import com.azarai.goworkbro.ui.components.LogEntryRow
import com.azarai.goworkbro.ui.components.NumberInputDialog
import com.azarai.goworkbro.ui.components.SectionLabel
import com.azarai.goworkbro.ui.components.WeekBars
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** 健身打卡页：目标分钟、快捷 +10/+30、自定义、今日记录、近 7 天。 */
@Composable
fun FitnessScreen(onBack: () -> Unit) {
    val vm: FitnessViewModel = viewModel()
    val state by vm.state.collectAsState()
    val tick = rememberTick()

    var customDialog by remember { mutableStateOf(false) }
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
        SectionLabel("记一笔运动")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(10, 30).forEach { min ->
                Surface(
                    onClick = { tick(); vm.log(min) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "+${min} 分钟",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Surface(
                onClick = { customDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "自定义",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
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
                    "今天还没有运动记录",
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

    if (customDialog) {
        NumberInputDialog(
            title = "运动了多少分钟？",
            initialValue = "",
            unit = "分钟",
            onDismiss = { customDialog = false },
            onConfirm = { m -> customDialog = false; tick(); vm.log(m) },
        )
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
