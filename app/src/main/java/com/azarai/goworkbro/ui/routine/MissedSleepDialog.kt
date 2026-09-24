package com.azarai.goworkbro.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.ui.theme.LocalForestExtras
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Shown when a wake check-in has no sleep to pair with (the user tapped 起床
 * twice in a row). Collects the missed bedtime, or marks an all-nighter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedSleepDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
    onAllNighter: () -> Unit,
) {
    val yesterday = LocalDate.now().minusDays(1)
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = yesterday.atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initialHour = 23, initialMinute = 0, is24Hour = true)
    var showPicker by remember { mutableStateOf(false) }
    val extras = LocalForestExtras.current

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("上次几点睡的？") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    DatePicker(state = dateState, showModeToggle = false)
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val millis = dateState.selectedDateMillis
                    val date = millis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    } ?: yesterday
                    onConfirm(LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute)))
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
            containerColor = extras.card,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上次是什么时候睡的？") },
        text = { Text("这次起床打卡前面没有入睡记录，补一下才能算出这次的睡眠时长。") },
        confirmButton = {
            TextButton(onClick = { showPicker = true }) { Text("选时间") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAllNighter) { Text("通宵了") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        containerColor = extras.card,
    )
}
