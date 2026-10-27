package com.azarai.goworkbro.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.draw.clip
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

/** The single home page: a 2-column grid of five modules (sleep spans the row). */
@Composable
fun HomeScreen(
    openRoute: (String) -> Unit,
    onOpenActiveTimer: () -> Unit,
    onOpenOverview: () -> Unit = {},
    onSproutMeasured: (androidx.compose.ui.geometry.Offset) -> Unit = {},
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsState()
    val timerVm: com.azarai.goworkbro.ui.timer.TimerViewModel = viewModel()
    val activeTimer by timerVm.active.collectAsState()
    // NB: `elapsed` is deliberately NOT collected here — it ticks 4x/s and
    // would recompose the whole home grid; ActiveTimerBar reads it itself
    val sessions by timerVm.sessions.collectAsState()

    val extras = LocalForestExtras.current
    val tick = rememberTick()
    // countdown tile label is minute-precise — refresh it twice a minute
    var minuteTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            minuteTick++
        }
    }
    val nowCd = java.time.LocalDateTime.now()
    fun countdownStatus(): String =
        com.azarai.goworkbro.core.CountdownOps.nextOf(state.countdowns, nowCd)
            ?.let { com.azarai.goworkbro.core.CountdownOps.shortLabel(it.target, nowCd) }
            ?: "去添加一个吧"
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
                // A row of zeros is not information — it only shows up once
                // there is something to report.
                if (state.focusSessions > 0) {
                    Text(
                        focusLine(state.focusSessions, state.focusMinutes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            // settings live behind "数据纵览" -> its bottom gear: one icon here
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
        // downloaded-update banner: tap commits a PackageInstaller session
        AnimatedVisibility(visible = state.pendingUpdate != null) {
            Column {
                state.pendingUpdate?.let { v ->
                    UpdateBanner(
                        version = v,
                        onInstall = { vm.installUpdate() },
                        onDismiss = { vm.dismissUpdate() },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
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
                    pill = {
                        QuickLogPill(
                            text = "喝 ${state.quickCupMl}ml",
                            label = "喝水打卡 ${state.quickCupMl} 毫升",
                        ) { vm.drinkWater(state.quickCupMl) }
                        Spacer(Modifier.width(4.dp))
                    },
                    onClick = { openRoute(Routes.WATER) },
                )
            }
            item {
                ModuleCard(
                    icon = "clock", title = "倒计时", pastelIndex = 7,
                    onClick = { openRoute(Routes.COUNTDOWN) },
                    status = countdownStatus(),
                )
            }
            item(span = { GridItemSpan(2) }) {
                ModuleCard(
                    icon = "moon", title = "睡眠", pastelIndex = 4,
                    onClick = { openRoute(Routes.SLEEP) },
                    wide = true,
                    status = state.todaySleepText?.let { "已睡 $it" } ?: "今天还没记录",
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
                    vm = timerVm,
                    active = a,
                    sessions = sessions,
                    onClick = onOpenActiveTimer,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Bottom bar shown while a focus timer runs in the background. The live
 *  elapsed read lives HERE so its 4Hz ticks recompose only this bar. */
@Composable
private fun ActiveTimerBar(
    vm: com.azarai.goworkbro.ui.timer.TimerViewModel,
    active: com.azarai.goworkbro.ui.timer.ActiveTimer,
    sessions: Int,
    onClick: () -> Unit,
) {
    val elapsedMs by vm.elapsed.collectAsState()
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

/** "new version downloaded" strip under the home header. */
@Composable
private fun UpdateBanner(version: String, onInstall: () -> Unit, onDismiss: () -> Unit) {
    val extras = LocalForestExtras.current
    Surface(
        onClick = onInstall,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "新版本 v$version 已下载",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "点这里安装更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "忽略",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * One-tap log pill for a home card: says what gets recorded ("喝 500ml"), not
 * "打卡". A plain Box, not a Surface — the 48dp minimum touch target of
 * Material buttons would stretch the icon row.
 */
@Composable
private fun QuickLogPill(text: String, label: String, onClick: () -> Unit) {
    val tick = rememberTick()
    Box(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { tick(); onClick() }
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
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
    /** Optional one-tap action pill on the icon row (only the water tile uses it). */
    pill: (@Composable () -> Unit)? = null,
    /** Full-row card (the sleep tile): icon left, title + status stacked right. */
    wide: Boolean = false,
) {
    val extras = LocalForestExtras.current
    CuteCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (wide) Modifier.heightIn(min = 92.dp) else Modifier.aspectRatio(1.08f)),
    ) {
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, extras.pastels[pastelIndex % extras.pastels.size], 56.dp, bob = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.textSecondary,
                        maxLines = 1,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "详情",
                    tint = extras.textSecondary,
                )
            }
            return@CuteCard
        }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, extras.pastels[pastelIndex % extras.pastels.size], 44.dp, bob = true)
                Spacer(Modifier.weight(1f))
                // the pill sits on the icon row (44dp tall) so it cannot change
                // the card's text rhythm
                pill?.invoke()
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "详情",
                    tint = extras.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            if (progress != null) {
                CuteProgressBar(progress, height = 8.dp)
                Spacer(Modifier.height(5.dp))
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = extras.textSecondary,
                maxLines = 1,
                // status lines are sometimes Latin ("0 / 2000 ml") and
                // sometimes CJK ("0 / 45 分钟"); the two fallback fonts size
                // their lines differently, so pin a minimum or the cards in
                // one grid row stop sharing a baseline
                modifier = Modifier.heightIn(min = 20.dp),
            )
        }
    }
}
