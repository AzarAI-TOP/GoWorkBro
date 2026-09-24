package com.azarai.goworkbro.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.ui.components.CuteTopBar
import com.azarai.goworkbro.ui.theme.LocalForestExtras

/** 设置页：目前只有「删除应用数据」一项，后续设置项也放这里。 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val extras = LocalForestExtras.current
    val lateNight by vm.lateNight.collectAsState()
    var showDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        CuteTopBar(title = "设置", onBack = onBack)

        Spacer(Modifier.size(6.dp))
        SettingSwitchRow(
            title = "熬夜模式",
            subtitle = "0 点到 4 点的打卡算作前一天",
            checked = lateNight,
            onCheckedChange = { vm.setLateNight(it) },
        )
        Spacer(Modifier.size(10.dp))
        SettingRow(
            title = "删除应用数据",
            subtitle = "按类型清理待办、打卡与记录",
            danger = true,
            onClick = { showDelete = true },
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

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val extras = LocalForestExtras.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = extras.card,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, extras.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val extras = LocalForestExtras.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = extras.card,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, extras.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DeleteOutline,
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
