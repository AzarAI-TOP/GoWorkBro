package com.azarai.goworkbro.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.core.Backup
import com.azarai.goworkbro.core.Updater
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** Settings page: updates, backup and the data wipe. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val extras = LocalForestExtras.current
    val autoUpdate by vm.autoUpdate.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember { Updater.currentVersion(context) }

    // SAF pickers: export writes a fresh JSON doc, import replaces all data
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importFrom(uri) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CuteTopBar(title = "设置", onBack = onBack)

        Spacer(Modifier.size(6.dp))
        SettingSwitchRow(
            title = "自动更新",
            subtitle = "在 GitHub 上检查新版本并自动下载，下载完在主页一键安装",
            checked = autoUpdate,
            onCheckedChange = { vm.setAutoUpdate(it) },
        )
        Spacer(Modifier.size(10.dp))
        SettingRow(
            title = "检查更新",
            subtitle = "立即去 GitHub 看看有没有新版本",
            icon = Icons.Filled.SystemUpdate,
            onClick = { vm.checkNow() },
        )
        InfoRow(title = "当前版本", value = "v$version")
        Spacer(Modifier.size(10.dp))
        SettingRow(
            title = "导出数据",
            subtitle = "把全部记录存成一个 JSON 备份文件（换机、刷机前记得导）",
            icon = Icons.Filled.Save,
            onClick = { exportLauncher.launch(Backup.fileName()) },
        )
        SettingRow(
            title = "导入数据",
            subtitle = "从备份文件恢复，会覆盖当前全部数据",
            icon = Icons.Filled.SettingsBackupRestore,
            onClick = { showImportConfirm = true },
            danger = true,
        )
        Spacer(Modifier.size(10.dp))
        SettingRow(
            title = "删除应用数据",
            subtitle = "按类型清理待办、打卡与记录",
            icon = Icons.Filled.DeleteOutline,
            onClick = { showDelete = true },
            danger = true,
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("确认导入？") },
            text = {
                Text("导入会先清空当前的待办、习惯、记录和设置，再写入备份文件的内容。\n建议先导出一份当前数据再继续。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                }) { Text("继续，选文件", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            },
            containerColor = extras.card,
        )
    }

    if (showDelete) {
        DeleteDataDialog(
            onDismiss = { showDelete = false },
            onConfirm = { kinds ->
                vm.deleteData(kinds)
                showDelete = false
            },
        )
    }
}

/** Shared settings-row scaffold: rounded card, optional tap, row content. */
@Composable
private fun SettingCard(
    vertical: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val extras = LocalForestExtras.current
    val shape = RoundedCornerShape(18.dp)
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp)
        .let { if (onClick == null) it else it.clip(shape).clickable(onClick = onClick) }
    Surface(shape = shape, color = extras.card, border = BorderStroke(1.5.dp, extras.cardBorder), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = vertical),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val extras = LocalForestExtras.current
    SettingCard(vertical = 10.dp) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extras.textSecondary)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val extras = LocalForestExtras.current
    SettingCard(vertical = 14.dp, onClick = onClick) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else extras.textSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extras.textSecondary)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = extras.textSecondary,
        )
    }
}

/** Non-interactive label/value row (e.g. the running version). */
@Composable
private fun InfoRow(title: String, value: String) {
    SettingCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = LocalForestExtras.current.textSecondary)
    }
}

/** Pick data types to wipe, then confirm (irreversible). */
@Composable
private fun DeleteDataDialog(onDismiss: () -> Unit, onConfirm: (Set<DataKind>) -> Unit) {
    var selected by remember { mutableStateOf(setOf<DataKind>()) }
    var confirming by remember { mutableStateOf(false) }
    val extras = LocalForestExtras.current

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("确认删除？") },
            text = {
                Text(
                    "将删除：" + selected.joinToString("、") { it.label } +
                        "\n此操作不可恢复哦。",
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(selected) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
            containerColor = extras.card,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除应用数据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DataKind.entries.forEach { kind ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (kind in selected) selected - kind else selected + kind
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = kind in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + kind else selected - kind
                            },
                        )
                        Text(
                            kind.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirming = true },
                enabled = selected.isNotEmpty(),
            ) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = extras.card,
    )
}
