@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.azarai.goworkbro.ui.countdown

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.components.AppCard
import com.azarai.goworkbro.ui.todo.ConfirmDeleteDialog
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.theme.ChartColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CountdownViewModel : ViewModel() {
    private val db = Graph.db

    val countdowns = db.countdownDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(existing: Countdown?, title: String, target: LocalDateTime, colorIndex: Int) {
        viewModelScope.launch {
            if (existing == null) {
                db.countdownDao().upsert(
                    Countdown(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        targetDatetime = target.atZone(ZoneId.systemDefault()).toInstant().toString(),
                        createdDate = Dates.nowIso(),
                        colorIndex = colorIndex,
                    ),
                )
            } else {
                db.countdownDao().upsert(
                    existing.copy(
                        title = title,
                        targetDatetime = target.atZone(ZoneId.systemDefault()).toInstant().toString(),
                        colorIndex = colorIndex,
                    ),
                )
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { db.countdownDao().deleteById(id) }
    }
}

fun parseTarget(value: String): LocalDateTime =
    runCatching {
        LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault())
    }.getOrDefault(LocalDateTime.now())

@Composable
fun CountdownRoute(vm: CountdownViewModel = viewModel()) {
    val countdowns by vm.countdowns.collectAsState()
    val colors = AppTheme.colors
    val haptics = LocalHapticFeedback.current

    var editTarget by remember { mutableStateOf<Countdown?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Countdown?>(null) }
    var sheetTarget by remember { mutableStateOf<Countdown?>(null) }

    // One tick per second while the screen is visible.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            ) {
                Text(
                    appString(R.string.tab_countdown),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
            }

            if (countdowns.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = colors.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            appString(R.string.no_countdowns),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            appString(R.string.add_countdown_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(countdowns, key = { it.id }) { cd ->
                        CountdownCard(
                            countdown = cd,
                            tick = tick,
                            onEdit = { sheetTarget = cd },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                editTarget = cd
                            },
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        ) {
            FloatingActionButton(
                onClick = { sheetTarget = Countdown(id = "", title = "") },
                containerColor = colors.primary,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Add, contentDescription = appString(R.string.add))
            }
        }
    }

    editTarget?.let { cd ->
        com.azarai.goworkbro.ui.todo.OptionsSheet(
            onEdit = { sheetTarget = cd; editTarget = null },
            onDelete = { deleteTarget = cd; editTarget = null },
            onDismiss = { editTarget = null },
        )
    }

    if (sheetTarget != null) {
        val isNew = sheetTarget?.id?.isEmpty() == true
        CountdownEditSheet(
            existing = if (isNew) null else sheetTarget,
            onDismiss = { sheetTarget = null },
            onSave = { title, target, colorIndex ->
                vm.save(if (isNew) null else sheetTarget, title, target, colorIndex)
                sheetTarget = null
            },
        )
    }

    deleteTarget?.let { cd ->
        ConfirmDeleteDialog(
            title = cd.title,
            onConfirm = {
                vm.delete(cd.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun CountdownCard(
    countdown: Countdown,
    tick: Int,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = AppTheme.colors
    val color = ChartColors[countdown.colorIndex % ChartColors.size]
    val target = parseTarget(countdown.targetDatetime)
    val now = LocalDateTime.now()
    val remainingSeconds = java.time.Duration.between(now, target).seconds
    val expired = remainingSeconds <= 0

    val created = runCatching { Dates.parseIso(countdown.createdDate) }.getOrDefault(now.minusDays(1))
    val totalSeconds = java.time.Duration.between(created, target).seconds
    val remainingRatio = if (totalSeconds > 0) {
        (remainingSeconds.toDouble() / totalSeconds).coerceIn(0.0, 1.0)
    } else {
        0.0
    }

    val days = (remainingSeconds / 86400).coerceAtLeast(0).toInt()
    val hours = ((remainingSeconds % 86400) / 3600).coerceAtLeast(0).toInt()
    val minutes = ((remainingSeconds % 3600) / 60).coerceAtLeast(0).toInt()
    val seconds = (remainingSeconds % 60).coerceAtLeast(0).toInt()

    AppCard(onClick = onEdit, onLongClick = onLongPress, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                CircularProgressIndicator(
                    progress = { if (expired) 0f else remainingRatio.toFloat() },
                    strokeWidth = 4.dp,
                    color = if (expired) colors.textMuted else color,
                    trackColor = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(72.dp),
                )
                Icon(
                    imageVector = if (expired) Icons.Filled.Check else Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = if (expired) colors.textMuted else color,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    countdown.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (expired) colors.textMuted else colors.textSecondary,
                    textDecoration = if (expired) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(8.dp))
                if (expired) {
                    Text(
                        appString(R.string.countdown_finished),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textMuted,
                    )
                } else {
                    Row {
                        if (days > 0) {
                            TimeUnit("%02d".format(days), appString(R.string.unit_days))
                            Spacer(Modifier.width(8.dp))
                        }
                        TimeUnit("%02d".format(hours), appString(R.string.unit_hours))
                        Spacer(Modifier.width(8.dp))
                        TimeUnit("%02d".format(minutes), appString(R.string.unit_minutes))
                        Spacer(Modifier.width(8.dp))
                        TimeUnit("%02d".format(seconds), appString(R.string.unit_seconds))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        appString(
                            R.string.countdown_target,
                            "%d/%d %02d:%02d".format(
                                target.monthValue,
                                target.dayOfMonth,
                                target.hour,
                                target.minute,
                            ),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint,
                    )
                }
            }
            Box(
                Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun TimeUnit(value: String, unit: String) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                fontFeatureSettings = "tnum",
            ),
        )
        Text(unit, fontSize = 12.sp, color = colors.textMuted, modifier = Modifier.padding(bottom = 1.dp))
    }
}

/** Add/edit sheet: title + date + time + 6-color picker (v1 parity). */
@Composable
private fun CountdownEditSheet(
    existing: Countdown?,
    onDismiss: () -> Unit,
    onSave: (title: String, target: LocalDateTime, colorIndex: Int) -> Unit,
) {
    val colors = AppTheme.colors
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var target by remember {
        mutableStateOf(existing?.let { parseTarget(it.targetDatetime) } ?: LocalDateTime.now().plusHours(1))
    }
    var colorIndex by remember { mutableIntStateOf(existing?.colorIndex ?: 0) }
    var titleError by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.card) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                appString(if (existing == null) R.string.new_countdown else R.string.edit_countdown),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                isError = titleError,
                label = { Text(appString(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("%d/%d/%d".format(target.year, target.monthValue, target.dayOfMonth))
                }
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("%02d:%02d".format(target.hour, target.minute))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(appString(R.string.color), style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (0 until 6).forEach { i ->
                    val selected = colorIndex == i
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(ChartColors[i], CircleShape)
                            .border(
                                width = 3.dp,
                                color = if (selected) colors.textPrimary else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { colorIndex = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            val enterTitleMsg = appString(R.string.enter_title)
            val mustBeFutureMsg = appString(R.string.target_must_be_future)
            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) {
                        titleError = true
                        errorText = enterTitleMsg
                        return@Button
                    }
                    if (!target.isAfter(LocalDateTime.now())) {
                        errorText = mustBeFutureMsg
                        return@Button
                    }
                    onSave(trimmed, target, colorIndex)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(appString(if (existing == null) R.string.create else R.string.save)) }
            errorText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = com.azarai.goworkbro.ui.theme.DangerRed, fontSize = 13.sp)
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        target = LocalDateTime.of(date, target.toLocalTime())
                    }
                    showDatePicker = false
                }) { Text(appString(R.string.confirm_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(appString(R.string.cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = target.hour,
            initialMinute = target.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    target = LocalDateTime.of(target.toLocalDate(), LocalTime.of(state.hour, state.minute))
                    showTimePicker = false
                }) { Text(appString(R.string.confirm_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(appString(R.string.cancel)) } },
            title = { Text(appString(R.string.pick_time)) },
            text = { TimePicker(state = state) },
        )
    }
}
