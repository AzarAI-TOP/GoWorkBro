package com.azarai.goworkbro.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.FocusPieChart
import com.azarai.goworkbro.ui.components.SectionLabel
import com.azarai.goworkbro.ui.components.SleepDurationChart
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** 数据纵览：总专注、今日占比饼图、近七天睡眠时长、底部齿轮（删数据）。 */
@Composable
fun OverviewScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val vm: OverviewViewModel = viewModel()
    val state by vm.state.collectAsState()
    val extras = LocalForestExtras.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CuteTopBar(title = "数据纵览", onBack = onBack)

        // ---- totals ----
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = extras.card,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, extras.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Row(
                Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TotalBlock(
                    value = "${state.totalSessions}",
                    unit = "次专注",
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(width = 1.dp, height = 40.dp)
                        .background(extras.divider),
                )
                val (value, unit) = Dates.minutesParts(state.totalMinutes)
                TotalBlock(
                    value = value,
                    unit = "${unit}专注",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "今日 ${state.todaySessions} 次 · ${Dates.formatMinutesHuman(state.todayMinutes)}",
            style = MaterialTheme.typography.bodySmall,
            color = extras.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )

        // ---- today's focus share ----
        SectionLabel("今日专注时间占比")
        Box(Modifier.padding(horizontal = 18.dp)) {
            FocusPieChart(slices = state.pie)
        }

        // ---- sleep duration ----
        SectionLabel("近七天睡眠时长")
        Box(Modifier.padding(horizontal = 18.dp)) {
            SleepDurationChart(points = state.sleep)
        }

        // ---- bottom gear ----
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(extras.inputFill)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = extras.textSecondary,
                )
            }
        }
    }

}

@Composable
private fun TotalBlock(value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(unit, style = MaterialTheme.typography.bodySmall, color = LocalForestExtras.current.textSecondary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = LocalForestExtras.current.textSecondary,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
    )
}
