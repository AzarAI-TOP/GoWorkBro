package com.azarai.goworkbro.ui.countdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.core.CountdownOps
import com.azarai.goworkbro.core.SleepOps
import com.azarai.goworkbro.core.db.Countdown
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.components.AddActionButton
import com.azarai.goworkbro.ui.components.ConfirmDeleteDialog
import com.azarai.goworkbro.ui.components.CuteCard
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.components.EmptyHint
import com.azarai.goworkbro.ui.components.IconBubble
import com.azarai.goworkbro.ui.theme.LocalForestExtras
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 倒计时页：条目列表 + 添加/编辑（标题 + 日期 + 时间）。 */
@Composable
fun CountdownScreen(onBack: () -> Unit) {
    val vm: CountdownViewModel = viewModel()
    val items by vm.items.collectAsState()
    val now by vm.now.collectAsState()
    val extras = LocalForestExtras.current

    var editing by remember { mutableStateOf<Countdown?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        CuteTopBar(
            title = "倒计时",
            onBack = onBack,
            action = { AddActionButton(onClick = { adding = true }) },
        )

        if (items.isEmpty()) {
            // centre both axes in the space under the top bar — a bare
            // EmptyHint in a Column sits at the start edge and looks broken
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyHint(icon = "clock", text = "加一个倒计时，掐着秒等它到")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, end = 18.dp, top = 6.dp, bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    CountdownRow(item = item, now = now, onEdit = { editing = item })
                }
            }
        }
    }

    if (adding) {
        CountdownEditDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { title, target, color ->
                vm.save(null, title, target, color)
                adding = false
            },
        )
    }
    editing?.let { item ->
        CountdownEditDialog(
            initial = item,
            onDismiss = { editing = null },
            onSave = { title, target, color ->
                vm.save(item.id, title, target, color)
                editing = null
            },
            onDelete = {
                vm.delete(item.id)
                editing = null
            },
        )
    }
}

@Composable
private fun CountdownRow(item: Countdown, now: LocalDateTime, onEdit: () -> Unit) {
    val extras = LocalForestExtras.current
    val expired = !item.target.isAfter(now)
    val remaining = CountdownOps.remainingLabel(item.target, now)
    val urgent = !expired && remaining.length <= 8 // "HH:mm:ss" or shorter
    CuteCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble("clock", extras.pastels[item.colorIndex % extras.pastels.size], 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${SleepOps.dateLabel(item.target)} ${Dates.hhmm(item.target)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.textSecondary,
                )
            }
            Text(
                remaining,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    expired -> extras.textSecondary
                    urgent -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/**
 * Add/edit dialog: title field + a target summary that opens a date picker
 * and then a time picker (both keep the already-chosen part).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownEditDialog(
    initial: Countdown?,
    onDismiss: () -> Unit,
    onSave: (String, LocalDateTime, Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var target by remember {
        mutableStateOf(initial?.target ?: LocalDateTime.now().plusDays(1).withSecond(0).withNano(0))
    }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val color = remember { initial?.colorIndex ?: (0..7).random() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), target, color) },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = { Text(if (initial == null) "新倒计时" else "编辑倒计时") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { pickDate = true }) {
                    Text("目标：${target.toLocalDate()} ${Dates.hhmm(target)}（点击修改）")
                }
            }
        },
        containerColor = LocalForestExtras.current.card,
    )

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = "删除这个倒计时？",
            text = "「${initial?.title.orEmpty()}」会被删掉，无法恢复。",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }

    if (pickDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = target.toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        if (date.isBefore(LocalDate.now())) {
                            target = LocalDate.now().atTime(target.toLocalTime())
                        } else {
                            target = date.atTime(target.toLocalTime())
                        }
                    }
                    pickDate = false
                    pickTime = true
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("取消") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (pickTime) {
        val timeState = rememberTimePickerState(
            initialHour = target.hour,
            initialMinute = target.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    target = target.toLocalDate().atTime(LocalTime.of(timeState.hour, timeState.minute))
                    pickTime = false
                }) { Text("好") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("取消") } },
            title = { Text("几点到？") },
            text = { TimePicker(state = timeState) },
            containerColor = LocalForestExtras.current.card,
        )
    }
}
