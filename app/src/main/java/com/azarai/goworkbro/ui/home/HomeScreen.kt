package com.azarai.goworkbro.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.ui.Routes
import com.azarai.goworkbro.ui.components.CuteCard
import com.azarai.goworkbro.ui.components.CuteProgressBar
import com.azarai.goworkbro.ui.components.IconBubble
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.routine.MissedSleepDialog
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.theme.LocalForestExtras
import java.time.LocalDate
import java.time.LocalDateTime

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun greet(now: LocalDateTime = LocalDateTime.now()): String = when (now.hour) {
    in 5..10 -> "早上好呀"
    in 11..12 -> "中午好呀"
    in 13..17 -> "下午好呀"
    in 18..22 -> "晚上好呀"
    else -> "夜深了"
}

private fun dateLine(now: LocalDateTime = LocalDateTime.now()): String {
    val d: LocalDate = now.toLocalDate()
    return "${d.monthValue}月${d.dayOfMonth}日 ${WEEKDAYS[d.dayOfWeek.value - 1]}"
}

/** "3 次专注 - 共专注 45 分钟" (minutes under an hour, else hours). */
private fun focusLine(sessions: Int, minutes: Int): String =
    "$sessions 次专注 - 共专注 ${Dates.formatMinutesHuman(minutes)}"

/** The single home page: a 2 x 3 grid of the six modules. */
@Composable
fun HomeScreen(
    openRoute: (String) -> Unit,
    onOpenActiveTimer: () -> Unit,
    onOpenOverview: () -> Unit = {},
    onSproutMeasured: (androidx.compose.ui.geometry.Offset) -> Unit = {},
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsState()
    var missedSleep by remember { mutableStateOf(false) }
    val timerVm: com.azarai.goworkbro.ui.timer.TimerViewModel = viewModel()
    val activeTimer by timerVm.active.collectAsState()
    val elapsed by timerVm.elapsed.collectAsState()
    val sessions by timerVm.sessions.collectAsState()

    val extras = LocalForestExtras.current
    val tick = rememberTick()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${greet()}，Azar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    dateLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = extras.textSecondary,
                )
                Text(
                    focusLine(state.focusSessions, state.focusMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconBubble(
                "sprout",
                extras.pastels[1],
                56.dp,
                bob = true,
                modifier = Modifier
                    .clickable(onClick = onOpenOverview)
                    .semantics { contentDescription = "数据纵览" }
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInWindow()
                        onSproutMeasured(
                            androidx.compose.ui.geometry.Offset(
                                p.x + coords.size.width / 2f,
                                p.y + coords.size.height / 2f,
                            ),
                        )
                    },
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                ModuleCard(
                    icon = "scroll", title = "待办", pastelIndex = 0,
                    status = when {
                        state.todoTotal == 0 -> "去添加一条吧"
                        state.todoUndone == 0 -> "全部完成 🎉"
                        else -> "${state.todoUndone} 项待完成"
                    },
                    onClick = { openRoute(Routes.TODOS) },
                )
            }
            item {
                ModuleCard(
                    icon = "sprout", title = "习惯", pastelIndex = 1,
                    status = when {
                        state.habitTotal == 0 -> "养成第一个习惯"
                        else -> "${state.habitDone}/${state.habitTotal} 已完成"
                    },
                    onClick = { openRoute(Routes.HABITS) },
                )
            }
            item {
                ModuleCard(
                    icon = "cup", title = "喝水", pastelIndex = 2,
                    status = "${state.waterMl} / ${state.waterGoal} ml",
                    progress = if (state.waterGoal == 0) 0f else state.waterMl.toFloat() / state.waterGoal,
                    onClick = { openRoute(Routes.WATER) },
                )
            }
            item {
                ModuleCard(
                    icon = "dumbbell", title = "健身", pastelIndex = 5,
                    status = "${state.fitnessMin} / ${state.fitnessGoal} 分钟",
                    progress = if (state.fitnessGoal == 0) 0f else state.fitnessMin.toFloat() / state.fitnessGoal,
                    onClick = { openRoute(Routes.FITNESS) },
                )
            }
            item {
                ModuleCard(
                    icon = "sun", title = "起床", pastelIndex = 6,
                    onClick = { openRoute(Routes.ROUTINE_WAKE) },
                    status = state.wakeTime ?: "还没起床打卡",
                    checkedTime = state.wakeTime,
                    onQuickCheck = { tick(); vm.checkInWake { missedSleep = true } },
                    quickCheckLabel = "起床打卡",
                )
            }
            item {
                ModuleCard(
                    icon = "moon", title = "睡觉", pastelIndex = 4,
                    onClick = { openRoute(Routes.ROUTINE_SLEEP) },
                    status = state.sleepTime ?: "还没睡觉打卡",
                    checkedTime = state.sleepTime,
                    onQuickCheck = { tick(); vm.checkInSleep() },
                    quickCheckLabel = "睡觉打卡",
                )
            }
        }
        // running focus task mini-card
        androidx.compose.animation.AnimatedVisibility(
            visible = activeTimer != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
        ) {
            activeTimer?.let { a ->
                ActiveTimerBar(
                    active = a,
                    elapsedMs = elapsed,
                    sessions = sessions,
                    onClick = onOpenActiveTimer,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
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

/** Bottom bar shown while a focus timer runs in the background. */
@Composable
private fun ActiveTimerBar(
    active: com.azarai.goworkbro.ui.timer.ActiveTimer,
    elapsedMs: Long,
    sessions: Int,
    onClick: () -> Unit,
) {
    val extras = LocalForestExtras.current
    CuteCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // left ~20%: icon
            Box(Modifier.weight(0.2f), contentAlignment = Alignment.Center) {
                IconBubble(
                    active.todo.icon,
                    extras.pastels[active.todo.colorIndex % extras.pastels.size],
                    46.dp,
                )
            }
            // right ~80%: content
            Column(Modifier.weight(0.8f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        active.todo.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (active.forward) "正" else "倒",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.textSecondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(thickness = 1.dp, color = extras.divider)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已进行 $sessions 次" + if (!active.isRunning) " · 已暂停" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!active.forward && active.durationMs != null) {
                        val percent = (elapsedMs * 100 / active.durationMs).toInt().coerceIn(0, 100)
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** One cute module card in the home grid. */
@Composable
private fun ModuleCard(
    icon: String,
    title: String,
    pastelIndex: Int,
    status: String,
    onClick: () -> Unit,
    progress: Float? = null,
    checkedTime: String? = null,
    onQuickCheck: (() -> Unit)? = null,
    quickCheckLabel: String? = null,
) {
    val extras = LocalForestExtras.current
    CuteCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.08f),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, extras.pastels[pastelIndex % extras.pastels.size], 44.dp, bob = true)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "详情",
                    tint = extras.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(3.dp))
            if (progress != null) {
                CuteProgressBar(progress, height = 8.dp)
                Spacer(Modifier.height(5.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                )
            } else if (onQuickCheck != null) {
                if (checkedTime != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Surface(
                            onClick = onQuickCheck,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics {
                                contentDescription = quickCheckLabel ?: "打卡"
                            },
                        ) {
                            Text(
                                "打卡",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            } else {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                )
            }
        }
    }
}
