package com.azarai.goworkbro.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.azarai.goworkbro.R
import com.azarai.goworkbro.core.Backup
import com.azarai.goworkbro.core.Store
import com.azarai.goworkbro.ui.EnvViewModel
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.components.AppCard
import com.azarai.goworkbro.ui.components.SegmentedControl
import com.azarai.goworkbro.ui.theme.AppTheme
import com.azarai.goworkbro.ui.theme.DangerRed
import kotlinx.coroutines.launch

/** Settings tab: language / theme / font / late-night + data management. */
@Composable
fun SettingsTab(env: EnvViewModel, vm: MeViewModel) {
    val colors = AppTheme.colors
    val locale by env.locale.collectAsState()
    val themeMode by env.themeMode.collectAsState()
    val fontChoice by env.fontChoice.collectAsState()
    val lateNight by env.lateNightMode.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val exportedMessage = appString(R.string.export_done)
    val exportFailedMessage = appString(R.string.export_failed)
    val importedMessage = appString(R.string.import_done)
    val importFailedMessage = appString(R.string.import_failed)

    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                Backup.export(context, uri)
                    .onSuccess { snackbar.showSnackbar(exportedMessage) }
                    .onFailure { snackbar.showSnackbar("$exportFailedMessage: ${it.message}") }
                busy = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Appearance
            AppCard {
                Column(Modifier.fillMaxWidth().padding(4.dp)) {
                    SettingLabel(appString(R.string.language))
                    Spacer(Modifier.padding(4.dp))
                    SegmentedControl(
                        options = listOf("zh", "en"),
                        selected = locale,
                        label = { if (it == "zh") "中文" else "English" },
                    ) { env.set(Store.Keys.LOCALE, it) }
                    Spacer(Modifier.padding(8.dp))
                    SettingLabel(appString(R.string.theme))
                    Spacer(Modifier.padding(4.dp))
                    SegmentedControl(
                        options = listOf("light", "dark", "system"),
                        selected = themeMode,
                        label = {
                            appString(
                                when (it) {
                                    "light" -> R.string.theme_light
                                    "dark" -> R.string.theme_dark
                                    else -> R.string.theme_system
                                },
                            )
                        },
                    ) { env.set(Store.Keys.THEME_MODE, it) }
                    Spacer(Modifier.padding(8.dp))
                    SettingLabel(appString(R.string.font))
                    Spacer(Modifier.padding(4.dp))
                    SegmentedControl(
                        options = listOf("system", "proto", "wenkai", "noto"),
                        selected = fontChoice,
                        label = {
                            appString(
                                when (it) {
                                    "system" -> R.string.font_system
                                    "proto" -> R.string.font_proto
                                    "wenkai" -> R.string.font_wenkai
                                    else -> R.string.font_noto
                                },
                            )
                        },
                    ) { env.set(Store.Keys.FONT_FAMILY, it) }
                }
            }

            // Late-night mode
            AppCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(appString(R.string.late_night_mode), style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
                        Text(
                            appString(R.string.late_night_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textFaint,
                        )
                    }
                    Switch(
                        checked = lateNight,
                        onCheckedChange = {
                            env.set(Store.Keys.LATE_NIGHT_MODE, it.toString())
                        },
                    )
                }
            }

            // Data management
            AppCard {
                Column(Modifier.fillMaxWidth().padding(4.dp)) {
                    Text(
                        appString(R.string.data_management),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.padding(6.dp))
                    ActionRow(Icons.Outlined.Download, appString(R.string.export_data), busy) {
                        exportLauncher.launch(Backup.suggestedFileName())
                    }
                    ActionRow(Icons.Outlined.Upload, appString(R.string.import_data), busy) {
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    }
                    ActionRow(Icons.Outlined.DeleteOutline, appString(R.string.delete_all_data), false, danger = true) {
                        confirmDelete = true
                    }
                }
            }

            // About
            AppCard {
                ActionRow(Icons.Outlined.Info, appString(R.string.about), false) {
                    showAbout = true
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(appString(R.string.delete_all_data)) },
            containerColor = colors.card,
            text = { Text(appString(R.string.confirm_delete_all)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAllData()
                }) { Text(appString(R.string.delete), color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(appString(R.string.cancel)) }
            },
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(appString(R.string.import_data)) },
            containerColor = colors.card,
            text = { Text(appString(R.string.confirm_import)) },
            confirmButton = {
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        Backup.import(context, uri)
                            .onSuccess { snackbar.showSnackbar(importedMessage) }
                            .onFailure { snackbar.showSnackbar("$importFailedMessage: ${it.message}") }
                        busy = false
                    }
                    pendingImportUri = null
                }) { Text(appString(R.string.confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text(appString(R.string.cancel)) }
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(appString(R.string.about)) },
            containerColor = colors.card,
            text = {
                Text(appString(R.string.about_body))
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(appString(R.string.confirm_ok)) }
            },
        )
    }
}

@Composable
private fun SettingLabel(text: String) {
    val colors = AppTheme.colors
    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textBody)
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) DangerRed else colors.textBody)
        Spacer(Modifier.padding(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) DangerRed else colors.textBody,
        )
        if (!enabled) {
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
            )
        }
    }
}
