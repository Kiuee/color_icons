package com.example.vivoicons.ui.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vivoicons.data.PendingEntry
import com.example.vivoicons.ui.PatchViewModel
import com.example.vivoicons.ui.components.EntryThumbnail
import com.example.vivoicons.ui.components.SwipeRevealBox
import com.example.vivoicons.ui.theme.AppShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TAB_TITLES = listOf("已添加", "已扫描")

/**
 * 队列管理页（规范 B + 需求 2）：Tab 切换「已添加 / 已扫描」，
 * 两个 Tab 均支持搜索定位（点击结果滚动定位并高亮）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueManagerScreen(vm: PatchViewModel) {
    val state by vm.ui.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var highlightPkg by remember { mutableStateOf<String?>(null) }
    var infoEntry by remember { mutableStateOf<PendingEntry?>(null) }
    var deleteIndex by remember { mutableIntStateOf(-1) }
    var deleteSelectedAsk by remember { mutableStateOf(false) }
    var revealedIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val addedListState = rememberLazyListState()
    val scannedListState = rememberLazyListState()
    val inSelection = state.selectionActive

    val scannedPackages = state.apkInfo?.existingPackages ?: emptyList()

    fun locate(index: Int, pkg: String) {
        query = ""
        scope.launch {
            val target = if (tab == 0) addedListState else scannedListState
            runCatching { target.scrollToItem(index) }
            highlightPkg = pkg
            delay(1500)
            if (highlightPkg == pkg) highlightPkg = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Text(
                            when {
                                inSelection -> "已选 ${state.selected.size} 项"
                                tab == 0 -> "已添加的包 (${state.queue.size})"
                                else -> "已扫描的包 (${scannedPackages.size})"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (inSelection) vm.exitSelection() else vm.back()
                        }) {
                            Icon(
                                if (inSelection) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    actions = {
                        if (inSelection) {
                            IconButton(onClick = { vm.toggleSelectAll() }) {
                                Icon(
                                    if (state.allSelected) Icons.Default.CheckBox
                                    else Icons.Outlined.CheckBoxOutlineBlank,
                                    contentDescription = "全选",
                                    tint = if (state.allSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    val first = state.selected.firstOrNull()
                                    if (state.selected.size == 1 && first != null) {
                                        vm.editFromQueue(first)
                                    } else {
                                        vm.editSelectedSequentially()
                                    }
                                },
                                enabled = state.selected.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑所选")
                            }
                            IconButton(
                                onClick = { deleteSelectedAsk = true },
                                enabled = state.selected.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除所选",
                                    tint = if (state.selected.isNotEmpty()) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    TAB_TITLES.forEachIndexed { i, title ->
                        Tab(
                            selected = tab == i,
                            onClick = {
                                tab = i
                                vm.exitSelection()
                                query = ""
                                highlightPkg = null
                            },
                            text = {
                                Text(
                                    if (i == 0) "已添加 (${state.queue.size})"
                                    else "已扫描 (${scannedPackages.size})",
                                )
                            },
                        )
                    }
                }
                SearchField(
                    value = query,
                    onValueChange = { query = it; highlightPkg = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (query.isNotBlank()) {
                LocateResultsRow(
                    all = if (tab == 0) state.queue.map { it.packageName } else scannedPackages,
                    query = query,
                    onClick = { pkg ->
                        val names = if (tab == 0) state.queue.map { it.packageName } else scannedPackages
                        val index = names.indexOfFirst { it.equals(pkg, ignoreCase = true) }
                        if (index >= 0) locate(index, pkg)
                    },
                )
            }

            when (tab) {
                0 -> {
                    if (state.queue.isEmpty()) {
                        EmptyHint("还没有添加任何包")
                    } else {
                        LazyColumn(
                            state = addedListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 20.dp, vertical = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                state.queue,
                                key = { i, e -> "a$i-${e.packageName}-${e.mcUri}" },
                            ) { index, entry ->
                                QueueItem(
                                    vm = vm,
                                    entry = entry,
                                    index = index,
                                    highlighted = highlightPkg != null &&
                                        entry.packageName.equals(highlightPkg!!, ignoreCase = true),
                                    selected = index in state.selected,
                                    inSelection = inSelection,
                                    revealed = revealedIndex == index,
                                    onRevealedChange = { open ->
                                        revealedIndex = if (open) index
                                        else if (revealedIndex == index) -1 else revealedIndex
                                    },
                                    onInfo = { infoEntry = entry },
                                    onEdit = { vm.editFromQueue(index) },
                                    onDelete = { deleteIndex = index },
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (scannedPackages.isEmpty()) {
                        EmptyHint("未能从 APK 中扫描出包名")
                    } else {
                        LazyColumn(
                            state = scannedListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 20.dp, vertical = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                scannedPackages,
                                key = { i, p -> "s$i-$p" },
                            ) { _, pkg ->
                                ScannedItem(
                                    pkg = pkg,
                                    highlighted = highlightPkg != null &&
                                        pkg.equals(highlightPkg!!, ignoreCase = true),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 资源详情弹窗（圆角）
    infoEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { infoEntry = null },
            shape = AppShapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text(entry.packageName) },
            text = {
                val prefix = entry.packageName.replace('.', '_')
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow("mc 资源文件", entry.mcName)
                    DetailRow("sc 资源文件", entry.scName ?: "（未选）")
                    HorizontalDivider()
                    DetailRow("将生成 mc", "${prefix}_b_s5_1x1_mc.xml")
                    DetailRow("将生成 sc", if (entry.scName != null) "${prefix}_b_s5_1x1_sc.xml" else "—")
                    DetailRow("将生成 bg", "${prefix}_b_s5_1x1_bg.xml（复制自模板）")
                    vm.thumbErrorFor(entry)?.let { err ->
                        HorizontalDivider()
                        Text(
                            "缩略图渲染失败：$err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoEntry = null }) { Text("知道了") }
            },
        )
    }

    if (deleteIndex >= 0) {
        ConfirmDialog(
            title = "删除该包？",
            text = "${state.queue.getOrNull(deleteIndex)?.packageName ?: ""} 将从队列中移除。",
            onConfirm = {
                vm.removeFromQueue(deleteIndex)
                deleteIndex = -1
            },
            onDismiss = { deleteIndex = -1 },
        )
    }

    if (deleteSelectedAsk) {
        ConfirmDialog(
            title = "删除所选 ${state.selected.size} 项？",
            text = "所选包将从队列中移除。",
            onConfirm = {
                vm.removeSelected()
                deleteSelectedAsk = false
            },
            onDismiss = { deleteSelectedAsk = false },
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("搜索包名") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清空")
                }
            }
        },
        shape = AppShapes.medium,
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier,
    )
}

/** 搜索匹配结果条：点击定位到列表中的项并高亮 */
@Composable
private fun LocateResultsRow(all: List<String>, query: String, onClick: (String) -> Unit) {
    val q = query.trim()
    val matches = if (q.isEmpty()) emptyList() else all.filter { it.contains(q, ignoreCase = true) }
    if (matches.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(matches) { _, pkg ->
            Surface(
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .clip(AppShapes.medium)
                    .clickable { onClick(pkg) },
            ) {
                Text(
                    pkg,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 已扫描包名的只读卡片（需求 2：不可编辑/删除，不影响队列） */
@Composable
private fun ScannedItem(pkg: String, highlighted: Boolean) {
    val container by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "scannedItem",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pkg,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    "已存在于目标 APK 中，不可编辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueItem(
    vm: PatchViewModel,
    entry: PendingEntry,
    index: Int,
    selected: Boolean,
    inSelection: Boolean,
    highlighted: Boolean,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onInfo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.secondaryContainer
            highlighted -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "queueItemColor",
    )
    SwipeRevealBox(
        revealed = revealed,
        onRevealedChange = onRevealedChange,
        endContent = {
            Row(
                modifier = Modifier.padding(start = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                FilledTonalIconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        when {
                            revealed -> onRevealedChange(false)
                            inSelection -> vm.toggleSelect(index)
                            else -> onInfo()
                        }
                    },
                    onLongClick = { if (!inSelection) vm.enterSelection(index) },
                ),
            shape = AppShapes.medium,
            colors = CardDefaults.cardColors(containerColor = container),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EntryThumbnail(vm = vm, entry = entry)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.packageName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        "mc: ${entry.mcName}" + (entry.scName?.let { " · sc: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (!inSelection) {
                    IconButton(onClick = onInfo) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "资源详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Icon(
                        if (selected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
