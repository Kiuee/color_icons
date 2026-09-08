package com.example.vivoicons.ui.steps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vivoicons.ui.PatchViewModel
import com.example.vivoicons.ui.UiState
import com.example.vivoicons.ui.theme.AppShapes

/** 第 2 步（规范 F）：包名 filled 圆角输入 + mc/sc 选择卡；确认/继续添加由右下角 FAB 区承担 */
@Composable
fun Step2Resources(vm: PatchViewModel) {
    val state by vm.ui.collectAsState()
    val scroll = rememberScrollState()

    val mcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.onMcPicked(it) }
    }
    val scPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.onScPicked(it) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.importFromFolder(it) }
    }
    val xmlMimes = arrayOf("text/xml", "application/xml", "*/*")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.multiEditQueue.isNotEmpty()) {
            // 顺序编辑进度：批量编辑 i/n + 进度条 + 当前正在编辑的包名
            val total = state.multiEditTotal.coerceAtLeast(1)
            val done = total - state.multiEditQueue.size
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "批量编辑 ${done + 1}/$total",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text(
                        "正在编辑：${state.packageNameInput}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        } else if (state.editingIndex >= 0) {
            Text(
                "正在编辑「${state.queue.getOrNull(state.editingIndex)?.packageName ?: ""}」，保存后返回队列。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextField(
            value = state.packageNameInput,
            onValueChange = vm::onPackageNameChange,
            label = { Text("应用包名") },
            placeholder = { Text(UiState.DEFAULT_PACKAGE) },
            supportingText = {
                if (state.packageNameInput.isNotEmpty() &&
                    !UiState.PACKAGE_REGEX.matches(state.packageNameInput.trim())
                ) {
                    Text("包名格式不正确，例如 com.coolapk.market")
                }
            },
            isError = state.packageNameInput.isNotEmpty() &&
                !UiState.PACKAGE_REGEX.matches(state.packageNameInput.trim()),
            singleLine = true,
            trailingIcon = {
                if (state.packageNameInput.isNotEmpty()) {
                    IconButton(onClick = { vm.onPackageNameChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "清空包名",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            shape = AppShapes.medium,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        ResourcePickerCard(
            label = "mc 资源（必选）",
            pickedName = state.mc?.name,
            onClick = { mcPicker.launch(xmlMimes) },
        )

        ResourcePickerCard(
            label = "sc 资源（可选）",
            pickedName = state.sc?.name,
            onClick = { scPicker.launch(xmlMimes) },
            onClear = if (state.sc != null) ({ vm.clearSc() }) else null,
        )

        // 批量导入（规范 3）
        FilledTonalButton(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.large,
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("批量导入（选择文件夹）")
        }
        Text(
            "请正确命名文件，如 com_coolapk_market_b_s5_1x1_mc.xml / _sc.xml",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.entryError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    // 批量导入结果
    state.importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { vm.dismissImportSummary() },
            shape = AppShapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("批量导入") },
            text = { Text(summary) },
            confirmButton = {
                TextButton(onClick = { vm.dismissImportSummary() }) { Text("知道了") }
            },
        )
    }
}

/** 整行 filled 圆角选择卡（规范 A：无 outlined） */
@Composable
private fun ResourcePickerCard(
    label: String,
    pickedName: String?,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                pickedName ?: "未选择，点击选择 XML 文件",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (pickedName != null) FontWeight.Medium else FontWeight.Normal,
                color = if (pickedName != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "清除 sc",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
