package com.azarai.goworkbro.ui.me

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.components.AppCard
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.theme.ChartColors

/** Stats tab: lifetime + today aggregates (v1 parity). */
@Composable
fun StatsTab(vm: MeViewModel) {
    val colors = AppTheme.colors
    val todos by vm.todos.collectAsState()
    val habits by vm.habits.collectAsState()
    val countdowns by vm.countdowns.collectAsState()
    val allSessions by vm.allSessions.collectAsState()
    val todaySessions by vm.todaySessions.collectAsState()
    val lifetimeTodos by vm.lifetimeTodosCompleted.collectAsState()
    val lifetimeHabits by vm.lifetimeHabitsCompleted.collectAsState()
    val firstUsed by vm.firstUsedDate.collectAsState()

    val lifetimeSeconds = allSessions.sumOf { it.durationSeconds }
    val todaySeconds = todaySessions.sumOf { it.durationSeconds }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                appString(R.string.lifetime_stats),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }
        item {
            StatCard(appString(R.string.total_focus), formatDuration(lifetimeSeconds), Icons.Outlined.AllInclusive, ChartColors[0])
        }
        item {
            StatCard(appString(R.string.total_sessions), allSessions.size.toString(), Icons.Outlined.LocalFireDepartment, ChartColors[3])
        }
        item {
            StatCard(appString(R.string.total_todos), lifetimeTodos.toString(), Icons.Outlined.TaskAlt, ChartColors[2])
        }
        item {
            StatCard(appString(R.string.total_habits), lifetimeHabits.toString(), Icons.Outlined.Repeat, ChartColors[1])
        }
        item {
            StatCard(appString(R.string.using_since), firstUsed.take(10), Icons.Outlined.CalendarMonth, ChartColors[4])
        }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Text(
                appString(R.string.today),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }
        item {
            StatCard(appString(R.string.today_focus), formatDuration(todaySeconds), Icons.Outlined.Timer, ChartColors[0])
        }
        item {
            StatCard(
                appString(R.string.pomodoro_count),
                appString(R.string.count_value, todaySessions.size),
                Icons.Outlined.LocalFireDepartment,
                ChartColors[3],
            )
        }
        item {
            StatCard(
                appString(R.string.todo_completed),
                "${todos.count { it.isCompleted }} / ${todos.size}",
                Icons.Outlined.CheckCircle,
                ChartColors[2],
            )
        }
        item {
            StatCard(
                appString(R.string.habit_completed),
                "${habits.count { it.isCompleted }} / ${habits.size}",
                Icons.Outlined.Repeat,
                ChartColors[1],
            )
        }
        item {
            StatCard(
                appString(R.string.active_countdowns),
                appString(R.string.count_value, countdowns.size),
                Icons.Outlined.HourglassEmpty,
                ChartColors[4],
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, color: Color) {
    val colors = AppTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textBody,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
