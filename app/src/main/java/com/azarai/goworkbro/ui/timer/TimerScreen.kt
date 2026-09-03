package com.azarai.goworkbro.ui.timer

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.Graph
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.core.db.TimingType
import com.azarai.goworkbro.core.util.Dates
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.todo.TodoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Full-screen timer (v1 parity). Elapsed time derives from wall-clock
 * timestamps, so it survives backgrounding and process death; active state
 * is persisted per-todo in user_settings and cleared on finish/abandon.
 */
@Composable
fun TimerScreen(todoId: String, onClose: () -> Unit, vm: TodoViewModel = viewModel()) {
    val colors = AppTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val todos by vm.todos.collectAsState()
    val todo = todos.firstOrNull { it.id == todoId }

    // ---- Timer state: accumulated ms + optional running-segment start ----
    var accumMs by remember { mutableLongStateOf(0L) }
    var runningStartMs by remember { mutableStateOf<Long?>(null) }
    var nowTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var finished by remember { mutableStateOf(false) }
    var sessionRecorded by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(false) }

    val timing = todo?.timing ?: TimingType.FORWARD
    val targetSeconds = if (timing == TimingType.BACKWARD) (todo?.durationMinutes ?: 25) * 60 else 0

    val recordFailedMessage = appString(R.string.record_save_failed)
    val notStartedMessage = appString(R.string.timer_not_started)

    // Restore persisted state once (survives process death).
    LaunchedEffect(todoId) {
        if (restored) return@LaunchedEffect
        restored = true
        val raw = Graph.store.get(Store.Keys.TIMER_ACTIVE) ?: return@LaunchedEffect
        runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("todo_id") == todoId) {
                accumMs = obj.optLong("accum_ms")
                runningStartMs = if (obj.isNull("running_start_ms")) {
                    null
                } else {
                    obj.optLong("running_start_ms")
                }
            }
        }
    }

    fun persist() {
        scope.launch {
            Graph.store.set(
                Store.Keys.TIMER_ACTIVE,
                JSONObject()
                    .put("todo_id", todoId)
                    .put("accum_ms", accumMs)
                    .put("running_start_ms", runningStartMs ?: JSONObject.NULL)
                    .toString(),
            )
        }
    }

    fun clearPersisted() {
        scope.launch { Graph.store.delete(Store.Keys.TIMER_ACTIVE) }
    }

    fun elapsedMs(): Long = accumMs + (runningStartMs?.let { nowTickMs - it } ?: 0L)

    fun elapsedSeconds(): Int = (elapsedMs() / 1000L).toInt()

    fun recordAndComplete() {
        scope.launch {
            runCatching {
                if (!sessionRecorded && elapsedSeconds() > 0) {
                    vm.recordFocusSession(todoId, todo?.title ?: "", elapsedSeconds())
                    sessionRecorded = true
                }
                vm.completeTodoWithDuration(todoId, elapsedSeconds())
                finished = true
                clearPersisted()
            }.onFailure {
                finished = false
                sessionRecorded = false
                snackbar.showSnackbar(recordFailedMessage)
            }
        }
    }

    // 250ms tick while running drives the display and auto-finish.
    LaunchedEffect(runningStartMs) {
        if (runningStartMs != null) {
            while (true) {
                nowTickMs = System.currentTimeMillis()
                if (timing == TimingType.BACKWARD &&
                    (accumMs + (nowTickMs - runningStartMs!!)) >= targetSeconds * 1000L
                ) {
                    accumMs = targetSeconds * 1000L
                    runningStartMs = null
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    recordAndComplete()
                    break
                }
                delay(250)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Persist latest state so reopening (or process death) resumes.
            if (!finished) {
                val running = runningStartMs
                if (running != null) {
                    accumMs += System.currentTimeMillis() - running
                }
                runningStartMs = null
                if (accumMs > 0) {
                    scope.launch {
                        Graph.store.set(
                            Store.Keys.TIMER_ACTIVE,
                            JSONObject()
                                .put("todo_id", todoId)
                                .put("accum_ms", accumMs)
                                .put("running_start_ms", JSONObject.NULL)
                                .toString(),
                        )
                    }
                }
            }
        }
    }

    BackHandler {
        when {
            runningStartMs != null -> showExitConfirm = true
            elapsedSeconds() > 0 -> showExitConfirm = true
            else -> onClose()
        }
    }

    val displaySeconds = if (timing == TimingType.BACKWARD) {
        (targetSeconds - elapsedSeconds()).coerceAtLeast(0)
    } else {
        elapsedSeconds()
    }
    val progress = if (timing == TimingType.BACKWARD && targetSeconds > 0) {
        (elapsedSeconds().toFloat() / targetSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }
    val isNoTiming = timing == TimingType.NONE

    Box(Modifier.fillMaxSize().background(colors.scaffold)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = { if (runningStartMs != null || elapsedSeconds() > 0) showExitConfirm = true else onClose() }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.textBody)
                }
                Text(
                    text = todo?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (!finished && elapsedSeconds() > 0) {
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            runningStartMs = null
                            recordAndComplete()
                        },
                    ) {
                        Text(
                            appString(if (isNoTiming) R.string.record else R.string.stop),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                    if (timing != TimingType.NONE) {
                        if (timing == TimingType.BACKWARD) {
                            CircularProgressIndicator(
                                progress = { progress },
                                strokeWidth = 6.dp,
                                color = colors.primary,
                                trackColor = colors.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(260.dp),
                            )
                        } else {
                            CircularProgressIndicator(
                                strokeWidth = 6.dp,
                                color = colors.primary,
                                trackColor = colors.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(260.dp),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = Dates.formatSeconds(displaySeconds),
                            style = TextStyle(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontFeatureSettings = "tnum",
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = appString(
                                when {
                                    timing != TimingType.BACKWARD && !isNoTiming -> R.string.forward_timer
                                    isNoTiming -> R.string.no_timer
                                    finished -> R.string.done
                                    else -> R.string.remaining
                                },
                            ),
                            fontSize = 14.sp,
                            color = colors.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(48.dp))

                if (finished) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        appString(R.string.focus_time, Dates.formatSeconds(elapsedSeconds())),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        appString(R.string.todo_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onClose,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    ) { Text(appString(R.string.back)) }
                } else {
                    Row(horizontalArrangement = Arrangement.Center) {
                        ControlButton(
                            icon = if (runningStartMs != null) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            label = appString(if (runningStartMs != null) R.string.pause else R.string.start),
                            color = colors.primary,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (runningStartMs == null && !finished) {
                                runningStartMs = System.currentTimeMillis()
                                nowTickMs = runningStartMs!!
                                persist()
                            } else {
                                accumMs += System.currentTimeMillis() - runningStartMs!!
                                runningStartMs = null
                                persist()
                            }
                        }
                        if (elapsedSeconds() > 0 || accumMs > 0) {
                            Spacer(Modifier.size(24.dp))
                            ControlButton(
                                icon = Icons.Filled.Stop,
                                label = appString(R.string.stop),
                                color = colors.primary,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                runningStartMs?.let { accumMs += System.currentTimeMillis() - it }
                                runningStartMs = null
                                if (elapsedSeconds() == 0) {
                                    scope.launch {
                                        snackbar.showSnackbar(notStartedMessage)
                                    }
                                } else {
                                    recordAndComplete()
                                }
                            }
                            Spacer(Modifier.size(24.dp))
                        }
                        ControlButton(
                            icon = Icons.Filled.Close,
                            label = appString(R.string.cancel),
                            color = colors.textMuted,
                        ) {
                            if (elapsedSeconds() > 0) showExitConfirm = true else onClose()
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(appString(R.string.exit_timer)) },
            containerColor = colors.card,
            text = {
                Text(
                    if (elapsedSeconds() > 0) {
                        appString(R.string.record_prompt, Dates.formatSeconds(elapsedSeconds()))
                    } else {
                        appString(R.string.confirm_exit_timer)
                    },
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showExitConfirm = false }) {
                        Text(appString(R.string.keep_timing))
                    }
                    if (elapsedSeconds() > 0) {
                        TextButton(
                            onClick = {
                                showExitConfirm = false
                                runningStartMs?.let { accumMs += System.currentTimeMillis() - it }
                                runningStartMs = null
                                recordAndComplete()
                                onClose()
                            },
                        ) { Text(appString(R.string.record_and_exit)) }
                    }
                    TextButton(onClick = {
                        showExitConfirm = false
                        clearPersisted()
                        onClose()
                    }) {
                        Text(appString(R.string.abandon), color = com.azarai.goworkbro.ui.theme.DangerRed)
                    }
                }
            },
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onTap: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onTap),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = color)
    }
}
