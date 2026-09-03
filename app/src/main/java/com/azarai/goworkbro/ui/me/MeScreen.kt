package com.azarai.goworkbro.ui.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azarai.goworkbro.R
import com.azarai.goworkbro.ui.EnvViewModel
import com.azarai.goworkbro.ui.appString
import com.azarai.goworkbro.ui.theme.AppTheme

/** Me tab: profile header + 打卡 / 统计 / 设置. */
@Composable
fun MeRoute(env: EnvViewModel, meVm: MeViewModel = viewModel()) {
    val colors = AppTheme.colors
    val userName by env.userName.collectAsState()
    val avatarExists by env.avatarExists.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }

    val avatarBitmap by remember(avatarExists) {
        mutableStateOf(if (avatarExists) com.azarai.goworkbro.Graph.avatars.load() else null)
    }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) env.setAvatar(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Profile header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(colors.primary, colors.secondary)),
                        CircleShape,
                    )
                    .clickable { showAvatarDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showNameDialog = true },
                ) {
                    Text(
                        userName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .background(colors.primary.copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "GoWorkBro v2",
                        fontSize = 12.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Sub-tabs
        val tabs = listOf(
            R.string.tab_check_in,
            R.string.tab_stats,
            R.string.tab_settings,
        )
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.scaffold,
            contentColor = colors.primary,
            indicator = { positions ->
                Box(
                    Modifier
                        .tabIndicatorOffset(positions[selectedTab])
                        .height(3.dp)
                        .background(colors.primary),
                )
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            appString(label),
                            color = if (selectedTab == index) colors.primary else colors.textMuted,
                        )
                    },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> SleepTab(meVm)
                1 -> StatsTab(meVm)
                else -> SettingsTab(env, meVm)
            }
        }
    }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            containerColor = colors.card,
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .background(
                                Brush.linearGradient(listOf(colors.primary, colors.secondary)),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(140.dp),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            showAvatarDialog = false
                            pickAvatar.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }) { Text(appString(R.string.change_avatar)) }
                        TextButton(onClick = { showAvatarDialog = false }) {
                            Text(appString(R.string.cancel))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(appString(R.string.edit_name)) },
            containerColor = colors.card,
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    env.setUserName(name)
                    showNameDialog = false
                }) { Text(appString(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text(appString(R.string.cancel)) }
            },
        )
    }
}
