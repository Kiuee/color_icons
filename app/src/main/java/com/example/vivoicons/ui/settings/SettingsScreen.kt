package com.example.vivoicons.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vivoicons.BuildConfig
import com.example.vivoicons.data.AppSettings
import com.example.vivoicons.ui.settings.SettingsViewModel
import com.example.vivoicons.ui.theme.AppShapes

/** 预设主题种子色 */
private val SEED_PRESETS = listOf(
    "ZI" to 0xFF5A5AC6.toInt(),
    "LAN" to 0xFF769CDF.toInt(),
    "LV" to 0xFF4C662B.toInt(),
    "ZONG" to 0xFFB33B15.toInt(),
    "HONG" to 0xFFCC1227.toInt(),
    "HUANG" to 0xFFFFDE3F.toInt(),
    "藕粉" to 0xFF8E4957.toInt(),
    "暖棕" to 0xFF6D4C41.toInt(),
)

/** 设置页：外观 / 更新 / 关于（三板块，白线分隔行） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    val update by vm.update.collectAsState()
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 底部 inset 已由外层处理（设置页经底栏进入时不会重复叠加）
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- 外观 ----------
            SectionCard(title = "外观") {
                SwitchRow(
                    title = "跟随系统主题色",
                    subtitle = "开启后使用系统壁纸取色（Android 12+）",
                    checked = settings.dynamicColor,
                    onChange = { vm.setDynamicColor(it) },
                )
                RowDivider()
                AnimatedVisibility(visible = !settings.dynamicColor) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "手动主题色",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SEED_PRESETS.forEach { (name, color) ->
                                SeedSwatch(
                                    color = color,
                                    selected = settings.seedColor == color,
                                    contentDescription = name,
                                    onClick = { vm.setSeedColor(color) },
                                )
                            }
                        }
                        RowDivider()
                    }
                }
                SwitchRow(
                    title = "深色模式跟随系统",
                    subtitle = "关闭后可手动指定浅色或深色",
                    checked = settings.darkMode == AppSettings.DARK_MODE_SYSTEM,
                    onChange = { follow ->
                        vm.setDarkMode(if (follow) AppSettings.DARK_MODE_SYSTEM else AppSettings.DARK_MODE_LIGHT)
                    },
                )
                AnimatedVisibility(visible = settings.darkMode != AppSettings.DARK_MODE_SYSTEM) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModeOption(
                            label = "浅色",
                            selected = settings.darkMode == AppSettings.DARK_MODE_LIGHT,
                            onClick = { vm.setDarkMode(AppSettings.DARK_MODE_LIGHT) },
                            modifier = Modifier.weight(1f),
                        )
                        ModeOption(
                            label = "深色",
                            selected = settings.darkMode == AppSettings.DARK_MODE_DARK,
                            onClick = { vm.setDarkMode(AppSettings.DARK_MODE_DARK) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---------- 更新 ----------
            SectionCard(title = "更新") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("当前版本", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                RowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("检查更新", style = MaterialTheme.typography.bodyMedium)
                        UpdateStateText(update)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (update is UpdateState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        FilledTonalButton(
                            onClick = { vm.checkUpdate() },
                            enabled = update !is UpdateState.Checking,
                            shape = AppShapes.medium,
                        ) { Text("检查") }
                    }
                }
                (update as? UpdateState.Available)?.let { available ->
                    RowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "发现新版本 ${available.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FilledTonalButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.url)))
                            },
                            shape = AppShapes.medium,
                        ) { Text("前往下载") }
                    }
                }
                RowDivider()
                SwitchRow(
                    title = "自动检查更新",
                    subtitle = "每次启动时在后台检查一次",
                    checked = settings.autoUpdate,
                    onChange = { vm.setAutoUpdate(it) },
                )
            }

            // ---------- 关于 ----------
            SectionCard(title = "关于") {
                InfoRow("版本", "v${BuildConfig.VERSION_NAME} · ARSCLib 1.3.8 引擎")
                RowDivider()
                InfoRow("应用", "vivo 图标资源注入工具")
                RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SettingsViewModel.REPO_URL)))
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("GitHub 仓库", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "color_icons",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        thickness = 1.dp,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "modeOption",
    )
    Box(
        modifier = modifier
            .clip(AppShapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeedSwatch(color: Int, selected: Boolean, contentDescription: String, onClick: () -> Unit) {
    val swatchColor = Color(color)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(swatchColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateStateText(update: UpdateState) {
    val text = when (update) {
        is UpdateState.Checking -> "正在检查…"
        is UpdateState.Latest -> "已是最新版本"
        is UpdateState.Available -> "发现新版本 ${update.version}"
        is UpdateState.Error -> update.message
        UpdateState.Idle -> "上次检查：从未"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = when (update) {
            is UpdateState.Error -> MaterialTheme.colorScheme.error
            is UpdateState.Available -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
