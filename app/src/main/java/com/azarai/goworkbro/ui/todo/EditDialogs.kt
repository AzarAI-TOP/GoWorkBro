@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.azarai.goworkbro.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.db.Habit
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.db.Todo
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.theme.DangerRed
import java.util.UUID

/** v1 TodoEditDialog: title, timing radio, duration chips, keep-tomorrow. */
@Composable
fun TodoEditDialog(
    initial: Todo?,
    onDismiss: () -> Unit,
    onSave: (todo: Todo, isNew: Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var timingType by rememberSaveable { mutableStateOf(initial?.timing?.name ?: TimingType.FORWARD.name) }
    var durationChoice by rememberSaveable {
        mutableStateOf(
            if (initial != null && initial.timing == TimingType.BACKWARD) {
                when (initial.durationMinutes) {
                    15 -> "15"
                    25 -> "25"
                    40 -> "40"
                    else -> "custom"
                }
            } else {
                "25"
            },
        )
    }
    var customDuration by rememberSaveable {
        mutableStateOf(
            if (initial != null && initial.timing == TimingType.BACKWARD) {
                initial.durationMinutes.toString()
            } else {
                "30"
            },
        )
    }
    var keepTomorrow by rememberSaveable { mutableStateOf(initial?.keepTomorrow ?: true) }
    var titleError by remember { mutableStateOf(false) }

    val timing = TimingType.valueOf(timingType)

    fun resolvedDuration(): Int {
        if (timing != TimingType.BACKWARD) return initial?.durationMinutes ?: 25
        return when (durationChoice) {
            "15" -> 15
            "25" -> 25
            "40" -> 40
            else -> (customDuration.trim().toIntOrNull() ?: 25).coerceAtLeast(1)
        }
    }

    val colors = AppTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(if (initial == null) R.string.add_todo else R.string.edit_todo)) },
        containerColor = colors.card,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textBody,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    isError = titleError,
                    label = { Text(appString(R.string.todo_title_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    appString(R.string.timing_method),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                listOf(
                    TimingType.FORWARD to R.string.forward_timer,
                    TimingType.BACKWARD to R.string.backward_timer,
                    TimingType.NONE to R.string.no_timer,
                ).forEach { (type, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { timingType = type.name }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(selected = timing == type, onClick = { timingType = type.name })
                        Text(
                            appString(label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textBody,
                        )
                    }
                }
                if (timing == TimingType.BACKWARD) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        appString(R.string.duration),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        (listOf("15", "25", "40") + "custom").forEach { choice ->
                            val selected = durationChoice == choice
                            val label = if (choice == "custom") appString(R.string.custom) else "${choice}min"
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) colors.primary else colors.primary.copy(alpha = 0.08f),
                                modifier = Modifier.clickable { durationChoice = choice },
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = if (selected) Color.White else colors.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    if (durationChoice == "custom") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customDuration,
                            onValueChange = { customDuration = it.filter(Char::isDigit) },
                            label = { Text(appString(R.string.custom_min_hint)) },
                            suffix = { Text("min") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { keepTomorrow = !keepTomorrow }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = keepTomorrow, onCheckedChange = { keepTomorrow = it })
                    Column {
                        Text(
                            appString(R.string.keep_tomorrow),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textBody,
                        )
                        Text(
                            appString(R.string.keep_tomorrow_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textFaint,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().isEmpty()) {
                        titleError = true
                        return@Button
                    }
                    keyboard?.hide()
                    val duration = resolvedDuration()
                    val result = if (initial == null) {
                        Todo(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(),
                            timingType = timing.raw,
                            durationMinutes = duration,
                            keepTomorrow = keepTomorrow,
                            createdDate = Dates.nowIso(),
                        )
                    } else {
                        initial.copy(
                            title = title.trim(),
                            timingType = timing.raw,
                            durationMinutes = duration,
                            keepTomorrow = keepTomorrow,
                        )
                    }
                    onSave(result, initial == null)
                },
            ) { Text(appString(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) }
        },
    )
}

private val PRESET_UNITS = listOf("次", "分钟", "小时", "个", "页", "道")

/** v1 HabitEditDialog: name, daily target, unit chips + custom memory. */
@Composable
fun HabitEditDialog(
    initial: Habit?,
    savedUnits: List<String>,
    onPersistCustomUnit: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (habit: Habit, isNew: Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var target by rememberSaveable { mutableStateOf((initial?.targetCount ?: 1).toString()) }
    var unit by rememberSaveable {
        mutableStateOf(initial?.unit ?: PRESET_UNITS.first())
    }
    var isCustomUnit by rememberSaveable {
        mutableStateOf(initial != null && initial.unit !in PRESET_UNITS)
    }
    var customUnit by rememberSaveable {
        mutableStateOf(if (initial != null && initial.unit !in PRESET_UNITS) initial.unit else "")
    }
    var titleError by remember { mutableStateOf(false) }

    val colors = AppTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(if (initial == null) R.string.add_habit else R.string.edit_habit)) },
        containerColor = colors.card,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textBody,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    isError = titleError,
                    label = { Text(appString(R.string.habit_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter(Char::isDigit) },
                    label = { Text("${appString(R.string.daily)} ${appString(R.string.target_count)}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    appString(R.string.unit),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    (PRESET_UNITS + savedUnits.filter { it !in PRESET_UNITS }).forEach { preset ->
                        val selected = !isCustomUnit && unit == preset
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) colors.primary else colors.primary.copy(alpha = 0.08f),
                            modifier = Modifier.clickable {
                                unit = preset
                                isCustomUnit = false
                            },
                        ) {
                            Text(
                                preset,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = if (selected) Color.White else colors.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    val selectedCustom = isCustomUnit
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selectedCustom) colors.primary else colors.primary.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { isCustomUnit = true },
                    ) {
                        Text(
                            appString(R.string.custom),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (selectedCustom) Color.White else colors.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (isCustomUnit) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUnit,
                        onValueChange = { customUnit = it },
                        label = { Text(appString(R.string.unit_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().isEmpty()) {
                        titleError = true
                        return@Button
                    }
                    val parsedTarget = (target.trim().toIntOrNull() ?: 1).coerceAtLeast(1)
                    var finalUnit = unit
                    if (isCustomUnit) {
                        finalUnit = customUnit.trim().ifEmpty { PRESET_UNITS.first() }
                        onPersistCustomUnit(finalUnit)
                    }
                    val result = if (initial == null) {
                        Habit(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(),
                            targetCount = parsedTarget,
                            unit = finalUnit,
                            createdDate = Dates.nowIso(),
                        )
                    } else {
                        initial.copy(
                            title = title.trim(),
                            targetCount = parsedTarget,
                            unit = finalUnit,
                        )
                    }
                    onSave(result, initial == null)
                },
            ) { Text(appString(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) }
        },
    )
}

/** Long-press options: edit / delete. */
@Composable
fun OptionsSheet(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.card) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.textBody)
                Spacer(Modifier.width(16.dp))
                Text(
                    appString(R.string.edit),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textBody,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = DangerRed)
                Spacer(Modifier.width(16.dp))
                Text(
                    appString(R.string.delete),
                    style = MaterialTheme.typography.bodyLarge,
                    color = DangerRed,
                )
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(R.string.delete)) },
        containerColor = colors.card,
        text = { Text(appString(R.string.confirm_delete_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(appString(R.string.delete), color = DangerRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(appString(R.string.cancel)) }
        },
    )
}
