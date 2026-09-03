package com.azarai.goworkbro.ui.today

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.db.FocusSession
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.Overlay
import com.azarai.goworkbro.ui.OverlayViewModel
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.components.AppCard
import com.azarai.goworkbro.ui.components.MarkdownText
import com.azarai.goworkbro.ui.components.PieChart
import com.azarai.goworkbro.ui.components.WeekBarChart
import com.azarai.goworkbro.ui.theme.AppTheme

@Composable
fun TodayRoute(overlays: OverlayViewModel, vm: TodayViewModel = viewModel()) {
    val colors = AppTheme.colors
    val sessions by vm.todaySessions.collectAsState()
    val weekly by vm.weeklySeconds.collectAsState()
    val logicalDate by vm.logicalDate.collectAsState()
    val news by vm.news.collectAsState()
    val newsLoading by vm.newsLoading.collectAsState()
    val newsError by vm.newsError.collectAsState()

    val latestNews = news.firstOrNull { it.date <= logicalDate } ?: news.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    appString(R.string.tab_today),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (newsLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp),
                        color = colors.textFaint,
                    )
                }
                IconButton(onClick = { vm.refreshNews(force = true) }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = appString(R.string.refresh),
                        tint = colors.textMuted,
                    )
                }
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    // Total focus today
                    AppCard {
                        Column(Modifier.fillMaxWidth().padding(4.dp)) {
                            Text(
                                appString(R.string.today_focus),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textFaint,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    Dates.formatSeconds(sessions.sumOf { it.durationSeconds }),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = colors.textPrimary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    appString(R.string.session_count, sessions.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = appString(R.string.focus_source)) {
                        val bySource = sessions.groupBy { it.sourceTitle }
                            .map { (name, list) -> name to list.sumOf { it.durationSeconds } }
                            .sortedByDescending { it.second }
                        PieChart(slices = bySource)
                    }
                }

                item {
                    SectionCard(title = appString(R.string.weekly_focus)) {
                        val today = Dates.parseKey(logicalDate)
                        val labels = (6 downTo 0).map { offset ->
                            today.minusDays(offset.toLong()).dayOfWeek.getDisplayName(
                                java.time.format.TextStyle.NARROW,
                                java.util.Locale.getDefault(),
                            )
                        }
                        WeekBarChart(
                            values = weekly.map { it / 3600f },
                            labels = labels,
                            valueLabel = { "%.1fh".format(it) },
                        )
                    }
                }

                item {
                    SectionCard(title = appString(R.string.today_sessions)) {
                        if (sessions.isEmpty()) {
                            Text(
                                appString(R.string.no_sessions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textFaint,
                            )
                        } else {
                            sessions.forEach { session ->
                                SessionRow(session)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                item {
                    // USTC news
                    AppCard {
                        Column(Modifier.fillMaxWidth().padding(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    appString(R.string.news_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.weight(1f))
                                latestNews?.let {
                                    Text(
                                        it.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textFaint,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                latestNews != null -> {
                                    Text(
                                        latestNews.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { overlays.open(Overlay.NewsReader(latestNews.date)) }) {
                                        Text(appString(R.string.view_full_news))
                                    }
                                }
                                newsError != null -> Column {
                                    Text(
                                        appString(R.string.news_error),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textMuted,
                                    )
                                    TextButton(onClick = { vm.refreshNews(force = true) }) {
                                        Text(appString(R.string.retry))
                                    }
                                }
                                else -> Text(
                                    appString(R.string.news_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textFaint,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    AppCard {
        Column(Modifier.fillMaxWidth().padding(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    val colors = AppTheme.colors
    val start = runCatching { Dates.parseIso(session.startTime) }.getOrNull()
    val end = runCatching { Dates.parseIso(session.endTime) }.getOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .background(colors.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.sourceTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textBody,
                maxLines = 1,
            )
            Text(
                text = if (start != null && end != null) {
                    "%02d:%02d – %02d:%02d".format(
                        start.hour,
                        start.minute,
                        end.hour,
                        end.minute,
                    )
                } else {
                    ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
            )
        }
        Text(
            Dates.formatSeconds(session.durationSeconds),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textMuted,
        )
    }
}
