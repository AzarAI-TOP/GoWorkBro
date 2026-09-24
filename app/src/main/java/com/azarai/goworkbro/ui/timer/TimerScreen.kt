package com.azarai.goworkbro.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.IconBubble
import com.azarai.goworkbro.ui.components.rememberTick
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Full-screen focus page: task name on top, big clock, pause / stop. */
@Composable
fun TimerScreen(vm: TimerViewModel, onClose: () -> Unit) {
    val active by vm.active.collectAsState()
    val elapsed by vm.elapsed.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val a = active ?: return

    val extras = LocalForestExtras.current
    val tick = rememberTick()
    val remaining = a.durationMs?.let { (it - elapsed).coerceAtLeast(0) }
    val clock = Dates.formatSeconds(((remaining ?: elapsed) / 1000).toInt())
    val typeLabel = (if (a.forward) "正向计时" else "倒计时") + if (!a.isRunning) " · 已暂停" else ""

    Column(
        Modifier
            .fillMaxSize()
            .background(extras.scaffold)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                a.todo.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { tick(); vm.completeTodo() }) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "完成待办",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        IconBubble(
            a.todo.icon,
            extras.pastels[a.todo.colorIndex % extras.pastels.size],
            88.dp,
            bob = true,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            clock,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(typeLabel, style = MaterialTheme.typography.bodyMedium, color = extras.textSecondary)

        Spacer(Modifier.weight(1.4f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            // pause / resume
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { vm.togglePause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (a.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (a.isRunning) "暂停" else "继续",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }
            // stop early (counts as one completed focus round)
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(extras.inputFill)
                    .clickable { tick(); vm.stop() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "提前结束",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.weight(0.4f))
        Text(
            "本任务已进行 $sessions 次",
            style = MaterialTheme.typography.bodySmall,
            color = extras.textSecondary,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
