@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.azarai.goworkbro.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.db.SleepRecord
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.components.AppCard
import com.azarai.goworkbro.ui.components.TrendLineChart
import com.azarai.goworkbro.ui.theme.AppTheme
import java.time.LocalDateTime

/** Check-in tab: today buttons, 7-day trend charts, recent history. */
@Composable
fun SleepTab(vm: MeViewModel) {
    val colors = AppTheme.colors
    val records by vm.sleepRecords.collectAsState()

    var pickSleep by remember { mutableStateOf(false) }
    var pickWake by remember { mutableStateOf(false) }
    var showWorkoutSheet by remember { mutableStateOf(false) }
    var editRecord by remember { mutableStateOf<SleepRecord?>(null) }
    var editChoiceSleep by remember { mutableStateOf(false) }
    var anchorDate by remember { mutableStateOf<String?>(null) }

    val today = Dates.todayKey()
    val sleepRowToday = Dates.sleepRecordDateKey(LocalDateTime.now())
    val wakeRecord = records.firstOrNull { it.recordDate == today }
    val workoutRecord = records.firstOrNull { it.recordDate == today }
    val sleepRecord = records.firstOrNull { it.recordDate == sleepRowToday }
        ?: records.firstOrNull { it.recordDate == today }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(4.dp)) {
                    Text(
                        appString(R.string.today_check_in),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckInButton(
                            label = appString(R.string.wake_up),
                            icon = Icons.Outlined.WbSunny,
                            value = wakeRecord?.wakeTime,
                            modifier = Modifier.weight(1f),
                        ) { pickWake = true }
                        CheckInButton(
                            label = appString(R.string.workout),
                            icon = Icons.Outlined.FitnessCenter,
                            value = workoutRecord?.workoutDurationMinutes?.let { appString(R.string.minutes_value, it) },
                            modifier = Modifier.weight(1f),
                        ) { showWorkoutSheet = true }
                        CheckInButton(
                            label = appString(R.string.sleep),
                            icon = Icons.Outlined.Bedtime,
                            value = sleepRecord?.sleepTime,
                            modifier = Modifier.weight(1f),
                        ) { pickSleep = true }
                    }
                }
            }
        }

        if (records.isNotEmpty()) {
            item {
                Text(
                    appString(R.string.sleep_trends),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                )
            }
            item { SleepTrendCharts(records) }
        }

        item {
            Text(
                appString(R.string.check_in_history),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
        }
        if (records.isEmpty()) {
            item {
                Text(
                    appString(R.string.no_check_in_records),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textFaint,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(records.take(10), key = { it.id }) { record ->
                HistoryCard(record) {
                    editRecord = record
                }
            }
        }
    }

    TimePickDialog(pickSleep or pickWake, isSleep = pickSleep, is24h = true) { hour, minute ->
        if (hour >= 0) {
            if (pickSleep) {
                vm.recordTimeCheckIn(isSleep = true, hour = hour, minute = minute)
            } else if (pickWake) {
                vm.recordTimeCheckIn(isSleep = false, hour = hour, minute = minute)
            }
        }
        pickSleep = false
        pickWake = false
    }

    editRecord?.let { record ->
        ModalBottomSheet(onDismissRequest = { editRecord = null }, containerColor = colors.card) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(
                    record.recordDate,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SheetRow(icon = Icons.Outlined.Bedtime, label = appString(R.string.sleep)) {
                    anchorDate = record.recordDate
                    editChoiceSleep = true
                    editRecord = null
                }
                SheetRow(icon = Icons.Outlined.WbSunny, label = appString(R.string.wake_up)) {
                    anchorDate = record.recordDate
                    editChoiceSleep = false
                    editRecord = null
                }
            }
        }
    }

    if (anchorDate != null) {
        TimePickDialog(visible = true, isSleep = editChoiceSleep, is24h = true) { hour, minute ->
            if (hour >= 0) {
                vm.recordTimeCheckIn(editChoiceSleep, hour, minute, anchorDate)
            }
            anchorDate = null
        }
    }

    if (showWorkoutSheet) {
        WorkoutSheet(
            initialDuration = workoutRecord?.workoutDurationMinutes,
            initialNote = workoutRecord?.note,
            onDismiss = { showWorkoutSheet = false },
            onSave = { duration, note ->
                vm.recordWorkout(duration, note)
                showWorkoutSheet = false
            },
        )
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textBody)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textBody)
    }
}

@Composable
private fun CheckInButton(
    label: String,
    icon: ImageVector,
    value: String?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val colors = AppTheme.colors
    val hasRecord = value != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                if (hasRecord) colors.primary.copy(alpha = 0.08f) else colors.inputFill,
                RoundedCornerShape(16.dp),
            )
            .then(
                if (hasRecord) {
                    Modifier.border(
                        1.dp,
                        colors.primary.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onTap)
            .padding(vertical = 18.dp, horizontal = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (hasRecord) colors.primary else colors.textMuted,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
        Spacer(Modifier.height(4.dp))
        Text(
            value ?: appString(R.string.not_checked_in),
            fontSize = 12.sp,
            fontWeight = if (hasRecord) FontWeight.SemiBold else FontWeight.Normal,
            color = if (hasRecord) colors.primary else colors.textMuted,
        )
    }
}

@Composable
private fun HistoryCard(record: SleepRecord, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val workoutSummary = record.workoutDurationMinutes?.let {
        appString(R.string.workout_summary, it, record.note ?: "")
    } ?: "—"
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    record.recordDate,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textBody,
                )
                Text(
                    appString(
                        R.string.sleep_summary,
                        record.wakeTime ?: "—",
                        workoutSummary,
                        record.sleepTime ?: "—",
                    ),
                    fontSize = 12.sp,
                    color = colors.textFaint,
                )
            }
        }
    }
}

/** 3 trend charts: wake time, bedtime, sleep duration (7 days). */
@Composable
private fun SleepTrendCharts(records: List<SleepRecord>) {
    val colors = AppTheme.colors
    val byDate = records.associateBy { it.recordDate }
    val today = java.time.LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val labels = days.map { "${it.monthValue}/${it.dayOfMonth}" }

    val wake = days.map { byDate[Dates.dateKeyOf(it)]?.wakeTime?.let { h -> Dates.hoursFromTime(h) } }
    val sleep = days.map { byDate[Dates.dateKeyOf(it)]?.sleepTime?.let { h -> Dates.hoursFromTime(h) } }
    val duration = days.map { day ->
        val record = byDate[Dates.dateKeyOf(day)]
        val sleepH = Dates.hoursFromTime(record?.sleepTime)
        val wakeH = Dates.hoursFromTime(record?.wakeTime)
        if (sleepH == null || wakeH == null) {
            null
        } else {
            // Bedtime after midnight (e.g. 01:00 -> 25.0) pairs with morning wake.
            val bed = if (sleepH < 12.0) sleepH + 24 else sleepH
            (wakeH + 24 - bed).coerceAtLeast(0.0)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            Column(Modifier.fillMaxWidth().padding(4.dp)) {
                Text(appString(R.string.wake_time), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                TrendLineChart(values = wake, labels = labels, valueFormatter = { Dates.formatHours(it) })
            }
        }
        AppCard {
            Column(Modifier.fillMaxWidth().padding(4.dp)) {
                Text(appString(R.string.bedtime), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                TrendLineChart(values = sleep, labels = labels, valueFormatter = { Dates.formatHours(it) })
            }
        }
        AppCard {
            Column(Modifier.fillMaxWidth().padding(4.dp)) {
                Text(appString(R.string.sleep_duration), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                TrendLineChart(values = duration, labels = labels, valueFormatter = { Dates.formatDurationHours(it) })
            }
        }
    }
}

@Composable
private fun TimePickDialog(
    visible: Boolean,
    isSleep: Boolean,
    is24h: Boolean,
    onPicked: (hour: Int, minute: Int) -> Unit,
) {
    if (!visible) return
    val colors = AppTheme.colors
    val now = LocalDateTime.now()
    val state = rememberTimePickerState(
        initialHour = now.hour,
        initialMinute = now.minute,
        is24Hour = is24h,
    )
    AlertDialog(
        onDismissRequest = { onPicked(-1, -1) },
        title = {
            Text(
                appString(
                    if (isSleep) R.string.select_sleep_time else R.string.select_wake_time,
                ),
            )
        },
        containerColor = colors.card,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(state.hour, state.minute) }) {
                Text(appString(R.string.confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onPicked(-1, -1) }) { Text(appString(R.string.cancel)) }
        },
    )
}

/** Workout sheet: preset durations + custom + description (v1 parity). */
@Composable
private fun WorkoutSheet(
    initialDuration: Int?,
    initialNote: String?,
    onDismiss: () -> Unit,
    onSave: (durationMinutes: Int, description: String) -> Unit,
) {
    val colors = AppTheme.colors
    var duration by remember { mutableStateOf(initialDuration ?: 30) }
    var customText by remember { mutableStateOf("") }
    var useCustom by remember { mutableStateOf(initialDuration != null && initialDuration !in listOf(15, 30, 45, 60)) }
    var note by remember { mutableStateOf(initialNote ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.card) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                appString(R.string.workout),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(15, 30, 45, 60).forEach { preset ->
                    val selected = !useCustom && duration == preset
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) colors.primary else colors.primary.copy(alpha = 0.08f),
                        modifier = Modifier.clickable {
                            duration = preset
                            useCustom = false
                        },
                    ) {
                        Text(
                            "${preset}min",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (selected) Color.White else colors.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                val selectedCustom = useCustom
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selectedCustom) colors.primary else colors.primary.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { useCustom = true },
                ) {
                    Text(
                        appString(R.string.custom),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (selectedCustom) Color.White else colors.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (useCustom) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter(Char::isDigit) },
                    label = { Text(appString(R.string.custom_min_hint)) },
                    suffix = { Text("min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(appString(R.string.workout_note_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val finalDuration = if (useCustom) {
                        customText.trim().toIntOrNull()?.coerceIn(1, 1440) ?: duration
                    } else {
                        duration
                    }
                    onSave(finalDuration, note.trim())
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(appString(R.string.save)) }
        }
    }
}
