package com.example.vivoicons.ui

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Xml
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vivoicons.data.PendingEntry
import com.example.vivoicons.data.PrefsRepository
import com.example.vivoicons.data.TargetApk
import com.example.vivoicons.engine.ApkInfo
import com.example.vivoicons.engine.ApkPatcher
import com.example.vivoicons.engine.PackageInjection
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** SAF 选择到的单个文件 */
data class PickedFile(val uri: String, val name: String)

/** 注入成功页信息（独立于工作流记忆的快照，清除记忆后仍保留，仅存内存） */
data class SuccessInfo(
    val fileName: String,
    val sizeBytes: Long,
    /** 展示用路径（下载目录为相对展示路径，降级时为绝对路径） */
    val displayPath: String,
    val outputUri: Uri?,
    /** 是否覆盖了下载目录中的同名文件 */
    val overwrittenExisting: Boolean,
)

data class UiState(
    /** 向导当前步骤（向导是单一路由，步骤由内部状态驱动） */
    val step: Int = 1,
    // 第 1 步
    val apk: TargetApk? = null,
    val apkInfo: ApkInfo? = null,
    val parsingApk: Boolean = false,
    /** 解析失败/包名校验失败的原因（非空时第 1 步显示错误） */
    val parseError: String? = null,
    // 第 2 步（临时输入，确定后进队列）
    val packageNameInput: String = DEFAULT_PACKAGE,
    val mc: PickedFile? = null,
    val sc: PickedFile? = null,
    /** 加入队列被拒绝的原因（如与已扫描包名重复） */
    val entryError: String? = null,
    /** 批量导入结果提示（非空时弹出对话框） */
    val importSummary: String? = null,
    val editingIndex: Int = -1,
    /** 多选顺序编辑：待编辑的索引队列（保存一个自动预填下一个） */
    val multiEditQueue: List<Int> = emptyList(),
    /** 顺序编辑的总数（进度指示用） */
    val multiEditTotal: Int = 0,
    val entryJustAdded: Boolean = false,
    val queue: List<PendingEntry> = emptyList(),
    // 队列管理页多选
    val selectionActive: Boolean = false,
    val selected: Set<Int> = emptySet(),
    // 第 3 步
    val injecting: Boolean = false,
    val progress: Float? = null,
    val logs: List<String> = emptyList(),
    val injectError: String? = null,
    // 徽章脉冲动画触发器（每次加入队列 +1）
    val badgePulse: Int = 0,
) {
    val queueBadge: Int get() = queue.size
    val canGoStep2: Boolean get() = apk != null && apkInfo != null && !parsingApk
    val canConfirmEntry: Boolean
        get() = PACKAGE_REGEX.matches(packageNameInput.trim()) && mc != null && !injecting
    val allSelected: Boolean get() = queue.isNotEmpty() && selected.size == queue.size

    companion object {
        const val DEFAULT_PACKAGE = "com.coolapk.market"
        /** 唯一允许的目标 APK 包名 */
        const val EXPECTED_APK_PACKAGE = "com.vivo.simpleiconthemeres"
        val PACKAGE_REGEX =
            Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}

class PatchViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsRepository(application)
    private val appContext = application
    private val cacheApk = File(application.cacheDir, "target.apk")
    private val patchOutput = File(application.cacheDir, "patched_unsigned.apk")

    private val patcher = ApkPatcher(
        log = { line -> appendLog(line) },
        progress = { fraction, _ ->
            _ui.update { it.copy(progress = fraction.takeIf { f -> f < 1f }) }
        },
    )

    /** 缩略图内存缓存（LRU，最多 64 条） */
    private val thumbCache = object : LinkedHashMap<String, ImageBitmap?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>): Boolean =
            size > 64
    }

    /** 缩略图渲染失败原因：key 同上 */
    private val thumbErrors = HashMap<String, String>()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** 注入成功信息（清除记忆后仍保留，供成功页展示/分享） */
    private val _success = MutableStateFlow<SuccessInfo?>(null)
    val success: StateFlow<SuccessInfo?> = _success.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = prefs.readOnce()
            val apk = saved.apk ?: return@launch
            if (!uriReadable(Uri.parse(apk.uri))) {
                // 记忆的文件已不可读，清空重来
                prefs.clear()
                return@launch
            }
            _ui.update {
                it.copy(
                    step = saved.step.coerceIn(1, 3),
                    apk = apk,
                    queue = saved.queue,
                )
            }
            parseApkInternal(Uri.parse(apk.uri), apk.name, apk.sizeBytes, keepBgTemplate = true)
        }
    }

    // ---------- 向导内部步骤（页面切换由 Navigation Compose 处理） ----------

    /** 步骤节点跳转（只能返回之前的步骤） */
    fun backToStep(step: Int) {
        _ui.update { st ->
            if (step in 1..3 && step < st.step) st.copy(step = step) else st
        }
        viewModelScope.launch { prefs.saveStep(_ui.value.step) }
    }

    /** 向导内前进一步 */
    fun nextStep() {
        val target = _ui.value.step + 1
        if (target <= 3) {
            _ui.update { it.copy(step = target) }
            viewModelScope.launch { prefs.saveStep(target) }
        }
    }

    /** 回到指定步骤（向导内部切换用） */
    fun goStep(step: Int) {
        if (step in 1..3) {
            _ui.update { it.copy(step = step) }
            viewModelScope.launch { prefs.saveStep(step) }
        }
    }

    // ---------- 第 1 步 ----------

    fun onApkPicked(uri: Uri) {
        takePersistable(uri)
        viewModelScope.launch(Dispatchers.IO) {
            val (name, size) = queryNameAndSize(uri)
            val target = TargetApk(uri.toString(), name, size, null)
            _ui.update { it.copy(apk = target, apkInfo = null, logs = emptyList()) }
            parseApkInternal(uri, name, size, keepBgTemplate = false)
        }
    }

    fun chooseBgTemplate(path: String) {
        _ui.update { st -> st.copy(apk = st.apk?.copy(bgTemplatePath = path)) }
        viewModelScope.launch { prefs.saveBgTemplate(path) }
    }

    // ---------- 第 2 步 ----------

    fun onPackageNameChange(value: String) =
        _ui.update { it.copy(packageNameInput = value, entryError = null) }

    fun onMcPicked(uri: Uri) {
        takePersistable(uri)
        viewModelScope.launch(Dispatchers.IO) {
            val (name, _) = queryNameAndSize(uri)
            _ui.update { it.copy(mc = PickedFile(uri.toString(), name), entryJustAdded = false, entryError = null) }
        }
    }

    fun onScPicked(uri: Uri) {
        takePersistable(uri)
        viewModelScope.launch(Dispatchers.IO) {
            val (name, _) = queryNameAndSize(uri)
            _ui.update { it.copy(sc = PickedFile(uri.toString(), name), entryJustAdded = false, entryError = null) }
        }
    }

    fun clearSc() = _ui.update { it.copy(sc = null, entryJustAdded = false) }

    /** 确认：入队（或保存编辑）。返回是否成功。顺序编辑时自动预填下一个。 */
    fun confirmEntry(): Boolean {
        val st = _ui.value
        val mc = st.mc ?: return false
        val pkg = st.packageNameInput.trim()
        if (!UiState.PACKAGE_REGEX.matches(pkg)) return false
        // 与目标 APK 已扫描出的包名重复时拒绝，避免资源 id 冲突
        if (st.apkInfo?.existingPackages?.any { it.equals(pkg, ignoreCase = true) } == true) {
            _ui.update {
                it.copy(entryError = "「$pkg」已存在于目标 APK 中，为避免资源 id 冲突无法重复导入")
            }
            return false
        }
        val entry = PendingEntry(pkg, mc.uri, mc.name, st.sc?.uri, st.sc?.name)
        val queue = st.queue.toMutableList()
        val editing = st.editingIndex in queue.indices
        if (editing) {
            queue[st.editingIndex] = entry
        } else {
            queue.removeAll { it.packageName.equals(pkg, ignoreCase = true) }
            queue += entry
        }
        _ui.update {
            it.copy(
                queue = queue,
                badgePulse = if (editing) it.badgePulse else it.badgePulse + 1,
                entryJustAdded = !editing,
            )
        }
        viewModelScope.launch { prefs.saveQueue(queue) }

        // 顺序编辑：保存后自动预填下一个待编辑项（不离开当前页）
        if (editing && st.multiEditQueue.isNotEmpty()) {
            val remaining = st.multiEditQueue - st.editingIndex
            if (remaining.isNotEmpty()) {
                _ui.update { it.copy(multiEditQueue = remaining) }
                startEditingAt(remaining.first())
            } else {
                _ui.update { it.copy(multiEditQueue = emptyList()) }
            }
        }
        return true
    }

    /** 继续添加：清空本步输入，回到新增模式（规范 F） */
    fun resetEntryInputs() {
        _ui.update {
            it.copy(
                packageNameInput = UiState.DEFAULT_PACKAGE,
                mc = null,
                sc = null,
                editingIndex = -1,
                entryJustAdded = false,
            )
        }
    }

    /** 预填指定条目进入编辑状态 */
    private fun startEditingAt(index: Int) {
        val entry = _ui.value.queue.getOrNull(index) ?: return
        _ui.update {
            it.copy(
                editingIndex = index,
                packageNameInput = entry.packageName,
                mc = PickedFile(entry.mcUri, entry.mcName),
                sc = entry.scUri?.let { u -> PickedFile(u, entry.scName ?: "") },
                entryJustAdded = false,
                selectionActive = false,
                selected = emptySet(),
            )
        }
    }

    /** 从队列管理页编辑单个条目（左滑编辑）；页面切换由 UI 的回调完成 */
    fun editFromQueue(index: Int) {
        startEditingAt(index)
    }

    /** 多选顺序编辑：逐个编辑选中项，带进度指示（「批量编辑 i/n」）；页面切换由 UI 回调完成 */
    fun editSelectedSequentially(): Boolean {
        val st = _ui.value
        if (st.selected.isEmpty()) return false
        val order = st.selected.toList().sorted()
        _ui.update { it.copy(multiEditQueue = order, multiEditTotal = order.size) }
        startEditingAt(order.first())
        return true
    }

    /** 是否还有后续待编辑项（顺序编辑未完成时 UI 不出栈） */
    val hasMoreToEdit: Boolean get() = _ui.value.multiEditQueue.isNotEmpty()

    /** 编辑保存成功且无剩余项时由 UI 调用：清除编辑状态（页面返回由 UI 完成） */
    fun finishEdit() {
        _ui.update {
            it.copy(editingIndex = -1, multiEditQueue = emptyList(), multiEditTotal = 0, entryJustAdded = false)
        }
    }

    /** 离开页面时的临时状态清理（Navigation onDispose 调用，不影响已保存数据） */
    fun resetTransient() {
        _ui.update { st ->
            st.copy(
                selectionActive = false,
                selected = emptySet(),
                editingIndex = if (st.editingIndex >= 0 || st.multiEditQueue.isNotEmpty()) -1 else st.editingIndex,
                multiEditQueue = emptyList(),
                multiEditTotal = 0,
                entryJustAdded = false,
            )
        }
    }

    fun removeFromQueue(index: Int) {
        val queue = _ui.value.queue.toMutableList()
        if (index !in queue.indices) return
        queue.removeAt(index)
        _ui.update { it.copy(queue = queue, selected = emptySet(), selectionActive = false) }
        viewModelScope.launch { prefs.saveQueue(queue) }
    }

    fun removeSelected() {
        val st = _ui.value
        if (st.selected.isEmpty()) return
        val queue = st.queue.filterIndexed { i, _ -> i !in st.selected }
        _ui.update { it.copy(queue = queue, selected = emptySet(), selectionActive = false) }
        viewModelScope.launch { prefs.saveQueue(queue) }
    }

    /** 批量导入：解析文件夹内按 <包名下划线>_b_s5_1x1_mc/sc.xml 命名的文件并加入队列 */
    fun importFromFolder(treeUri: Uri) {
        takePersistable(treeUri)
        viewModelScope.launch(Dispatchers.IO) {
            val scanned = _ui.value.apkInfo?.existingPackages
                ?.map { it.lowercase() }?.toSet() ?: emptySet()
            val summary = runCatching { doImport(treeUri, scanned) }.getOrElse { e ->
                _ui.update { it.copy(importSummary = "导入失败：${e.message}") }
                return@launch
            }
            val newQueue = summary.queue
            if (newQueue != null) {
                prefs.saveQueue(newQueue)
            }
            _ui.update {
                it.copy(
                    queue = newQueue ?: it.queue,
                    importSummary = "成功导入 ${summary.added} 个包（覆盖 ${summary.overwritten} 个），跳过 ${summary.skipped} 个",
                )
            }
        }
    }

    private class ImportAcc {
        var added = 0
        var overwritten = 0
        var skipped = 0
        var queue: List<PendingEntry>? = null
    }

    private suspend fun doImport(
        treeUri: Uri,
        scanned: Set<String>,
    ): ImportAcc {
        val acc = ImportAcc()
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(appContext, treeUri)
            ?: throw IllegalStateException("无法读取所选文件夹")
        val mcMap = LinkedHashMap<String, androidx.documentfile.provider.DocumentFile>()
        val scMap = HashMap<String, androidx.documentfile.provider.DocumentFile>()
        val fileRegex = Regex("^(.+)_b_s5_1x1_(mc|sc)\\.xml$", RegexOption.IGNORE_CASE)
        for (f in root.listFiles()) {
            val name = f.name ?: continue
            if (!f.isFile) continue
            val m = fileRegex.matchEntire(name)
            if (m == null) {
                acc.skipped++ // 命名不合规（含 bg 文件——批量导入只需 mc/sc）
                continue
            }
            val pkg = m.groupValues[1].replace('_', '.')
            if (m.groupValues[2].equals("mc", ignoreCase = true)) mcMap[pkg] = f else scMap[pkg] = f
        }
        val queue = _ui.value.queue.toMutableList()
        for ((pkg, mc) in mcMap) {
            if (pkg.lowercase() in scanned) {
                acc.skipped++ // 与目标 APK 已有包名重复，避免 id 冲突
                continue
            }
            val sc = scMap[pkg]
            val entry = PendingEntry(
                packageName = pkg,
                mcUri = mc.uri.toString(),
                mcName = mc.name ?: "",
                scUri = sc?.uri?.toString(),
                scName = sc?.name,
            )
            val idx = queue.indexOfFirst { it.packageName.equals(pkg, ignoreCase = true) }
            if (idx >= 0) {
                queue[idx] = entry
                acc.overwritten++
            } else {
                queue.add(entry)
                acc.added++
            }
        }
        // 只有 sc 没有 mc 的组合无法注入
        acc.skipped += scMap.count { it.key !in mcMap }
        if (acc.added + acc.overwritten > 0) {
            _ui.update { it.copy(queue = queue) }
            acc.queue = queue
        }
        return acc
    }

    fun dismissImportSummary() = _ui.update { it.copy(importSummary = null) }

    // ---------- 队列管理页：多选 ----------

    fun enterSelection(index: Int) =
        _ui.update { it.copy(selectionActive = true, selected = setOf(index)) }

    fun toggleSelect(index: Int) = _ui.update {
        val sel = it.selected.toMutableSet()
        if (!sel.add(index)) sel.remove(index)
        it.copy(selected = sel)
    }

    fun toggleSelectAll() = _ui.update {
        if (it.allSelected) it.copy(selected = emptySet())
        else it.copy(selected = it.queue.indices.toSet())
    }

    fun exitSelection() = _ui.update { it.copy(selectionActive = false, selected = emptySet()) }

    // ---------- 第 3 步 ----------

    fun startInject() {
        val st = _ui.value
        val apk = st.apk ?: return
        if (st.injecting || st.queue.isEmpty()) return
        if (!st.canGoStep2) return
        _ui.update {
            it.copy(
                injecting = true,
                logs = emptyList(),
                injectError = null,
                progress = 0f,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                appendLog("从源文件加载 APK …")
                copyUriToFile(Uri.parse(apk.uri), cacheApk)

                val injections = st.queue.mapNotNull { entry ->
                    try {
                        PackageInjection(
                            packageName = entry.packageName,
                            mcBytes = readUriBytes(Uri.parse(entry.mcUri)),
                            scBytes = entry.scUri?.let { readUriBytes(Uri.parse(it)) },
                        )
                    } catch (e: Exception) {
                        appendLog("读取 ${entry.packageName} 的资源失败：${e.message}")
                        null
                    }
                }
                if (injections.isEmpty()) throw IllegalStateException("没有任何可注入的资源组")

                val bgPath = apk.bgTemplatePath
                    ?: st.apkInfo?.bgTemplates?.firstOrNull()
                    ?: throw IllegalStateException("APK 中没有 *_b_s5_1x1_bg.xml 背景模板")

                val report = patcher.patch(cacheApk, bgPath, injections, patchOutput)
                val failed = report.outcomes.filter { it.error != null }
                if (report.verifiedResNames.isEmpty()) {
                    throw IllegalStateException("自检失败，输出已丢弃")
                }
                if (failed.isNotEmpty()) {
                    appendLog("部分包失败：" + failed.joinToString("、") { o -> o.packageName })
                }

                // 打包完成：检测输出目录同名文件，直接覆盖（删除旧的再写入）
                val outName = outputNameFor(st.apk?.name ?: "target.apk")
                var overwrote = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = appContext.contentResolver
                    overwrote = deleteExistingInSubdir(resolver, outName) > 0
                }
                publishAndEnterSuccess(outName, overwrote)
            } catch (e: Exception) {
                appendLog("注入失败：${e.message}")
                _ui.update { it.copy(injectError = e.message, progress = null) }
            } finally {
                _ui.update { it.copy(injecting = false) }
            }
        }
    }

    /** 输出文件名与原 APK 保持一致（仅确保 .apk 后缀） */
    private fun outputNameFor(sourceName: String): String =
        if (sourceName.endsWith(".apk", ignoreCase = true)) sourceName else "$sourceName.apk"

    /** 按精确文件名查询，代码内过滤出输出子目录的条目（不受 RELATIVE_PATH 斜杠格式影响） */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryOwnedDownloads(
        resolver: android.content.ContentResolver,
        displayName: String,
    ): List<Uri> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val result = mutableListOf<Uri>()
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                null,
            )?.use { c ->
                val relIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val rel = c.getString(relIdx) ?: ""
                    if (rel.trimEnd('/').equals(ENV_SUBDIR, ignoreCase = true)) {
                        result += android.content.ContentUris.withAppendedId(
                            collection, c.getLong(0),
                        )
                    }
                }
            }
        }
        return result
    }

    /** 删除输出子目录中的同名旧文件，返回实际删除的行数 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteExistingInSubdir(
        resolver: android.content.ContentResolver,
        displayName: String,
    ): Int {
        var rows = 0
        for (uri in queryOwnedDownloads(resolver, displayName)) {
            runCatching { resolver.delete(uri, null, null) }.getOrNull()?.let { rows += it }
        }
        return rows
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryDisplayNameOf(resolver: android.content.ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()

    /** 改名并回读验证，确保真正生效 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renameTo(resolver: android.content.ContentResolver, uri: Uri, name: String): Boolean {
        val updated = runCatching {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) },
                null, null,
            )
        }.getOrNull() ?: 0
        return updated > 0 && queryDisplayNameOf(resolver, uri) == name
    }

    /** 写出文件，生成成功快照，清除过程缓存与全部记忆，进入成功页 */
    private suspend fun publishAndEnterSuccess(outName: String, overwrote: Boolean) {
        val resolver = appContext.contentResolver
        var overwroteFinal = overwrote
        var outputUri: Uri? = null
        val displayPath: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val saved = saveToDownloads(resolver, outName)
            if (saved != null) {
                outputUri = saved.first
                overwroteFinal = overwroteFinal || saved.second
            }
        }
        if (outputUri != null) {
            displayPath = "$ENV_SUBDIR/$outName"
        } else {
            val fallback = saveToAppDir(outName)
            outputUri = fallback?.first
            displayPath = fallback?.second ?: "未知"
        }
        // 成功快照（在删除缓存前取大小）
        val info = SuccessInfo(
            fileName = outName,
            sizeBytes = patchOutput.length(),
            displayPath = displayPath,
            outputUri = outputUri,
            overwrittenExisting = overwroteFinal,
        )
        // 清理过程文件与全部记忆
        runCatching { cacheApk.delete() }
        runCatching { patchOutput.delete() }
        prefs.clear()
        _success.value = info
        _ui.value = UiState()
    }

    /**
     * 写入下载目录，返回 uri 与是否真的覆盖了同名旧文件。
     * 覆盖判定以最终实际状态为准：
     * 1) 插入后回读文件名——被 MediaStore 自动改名（追加 "(1)" 后缀）即发生同名碰撞；
     * 2) 碰撞后删除占用者（统计真实删除行数）并把新文件改回目标名（改名后回读验证）；
     * 3) 若改名仍失败，删除带后缀的副本重新干净插入（旧占用者已删，不会再次碰撞）；
     * 4) 只有「旧文件确实被删 + 新文件最终就叫目标名」才报告覆盖成功。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(resolver: android.content.ContentResolver, outName: String): Pair<Uri, Boolean>? {
        val uri = insertAndWrite(resolver, outName) ?: return null
        val actualName = queryDisplayNameOf(resolver, uri)
        if (actualName == outName) return uri to false

        // 发生同名碰撞：删除占用者 → 改回目标名（每步都验证）
        var deletedRows = deleteExistingInSubdir(resolver, outName)
        if (renameTo(resolver, uri, outName)) return uri to (deletedRows > 0)

        // 索引可能滞后：再删再改一次
        deletedRows += deleteExistingInSubdir(resolver, outName)
        if (renameTo(resolver, uri, outName)) return uri to (deletedRows > 0)

        // 终极兜底：删掉带后缀的副本，重新干净插入
        runCatching { resolver.delete(uri, null, null) }
        val uri2 = insertAndWrite(resolver, outName) ?: return null
        val finalName = queryDisplayNameOf(resolver, uri2)
        if (finalName != outName) {
            // 连干净插入都被改写：无法保证覆盖，如实报告未覆盖
            return uri2 to false
        }
        return uri2 to (deletedRows > 0)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertAndWrite(resolver: android.content.ContentResolver, outName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, outName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, ENV_SUBDIR)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                patchOutput.inputStream().use { it.copyTo(out) }
            }
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** 降级保存到应用目录（只保留最新一个副本），返回 uri + 绝对路径 */
    private fun saveToAppDir(outName: String): Pair<Uri, String>? {
        val dir = File(appContext.getExternalFilesDir(null), "vivoicons_patched").apply { mkdirs() }
        // 只保留最新一个副本
        dir.listFiles()?.forEach { if (it.name != outName) runCatching { it.delete() } }
        val file = File(dir, outName)
        patchOutput.copyTo(file, overwrite = true)
        val uri = FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", file)
        return uri to file.absolutePath
    }

    fun buildShareIntent(): Intent {
        val info = _success.value
        val uri = info?.outputUri ?: return Intent()
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(info.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 成功页「确定」：清除成功信息回到首页 */
    fun finishSuccess() {
        _success.value = null
        _ui.update { it.copy(step = 1) }
    }

    /** 清除全部记忆并回到首页 */
    fun clearMemory() {
        viewModelScope.launch {
            prefs.clear()
            _success.value = null
            _ui.value = UiState()
        }
    }

    /** 清除记忆并彻底退出（首页双击返回的第二击调用）。同步清空确保退出前完成。 */
    fun clearMemoryAndExit() {
        kotlinx.coroutines.runBlocking { prefs.clear() }
        _success.value = null
        _ui.value = UiState()
    }

    // ---------- 缩略图（队列管理页：mc 底 + sc 叠加） ----------

    private fun thumbKey(entry: PendingEntry) = entry.mcUri + "|" + (entry.scUri ?: "")

    /** 该条目缩略图渲染失败的原因（null = 成功或未尝试） */
    fun thumbErrorFor(entry: PendingEntry): String? = thumbErrors[thumbKey(entry)]

    suspend fun loadThumb(entry: PendingEntry): ImageBitmap? {
        val key = thumbKey(entry)
        thumbCache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = try {
                renderThumb(entry)
            } catch (e: Exception) {
                thumbErrors[key] = e.message ?: e.javaClass.simpleName
                android.util.Log.w("ThumbRender", "缩略图渲染失败", e)
                null
            }
            thumbCache[key] = bitmap
            bitmap
        }
    }

    private fun renderThumb(entry: PendingEntry): ImageBitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawDrawable(canvas, entry.mcUri, size)
        entry.scUri?.let { drawDrawable(canvas, it, size) }
        return bmp.asImageBitmap()
    }

    private fun drawDrawable(canvas: Canvas, uriStr: String, size: Int) {
        val drawable = drawableFromUri(uriStr) ?: throw IllegalArgumentException("无法识别的 drawable 类型")
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = min(size.toFloat() / w, size.toFloat() / h) * 0.9f
        val dw = (w * scale).toInt().coerceAtLeast(1)
        val dh = (h * scale).toInt().coerceAtLeast(1)
        drawable.setBounds((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2)
        drawable.draw(canvas)
    }

    private fun drawableFromUri(uriStr: String): Drawable? = try {
        var bytes = readUriBytes(Uri.parse(uriStr))
        // 二进制 XML（APK 内编译资源）魔数 0x00 0x03 之后的 0x03 0x00：文本解析必然失败，直接给出明确原因
        if (bytes.size >= 4 && bytes[0] == 0x03.toByte() && bytes[1] == 0x00.toByte()) {
            throw IllegalArgumentException("二进制 XML（从 APK 提取的编译资源），无法独立渲染")
        }
        // 去掉 UTF-8 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes = bytes.copyOfRange(3, bytes.size)
        }
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var type = parser.next()
        while (type != org.xmlpull.v1.XmlPullParser.START_TAG &&
            type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
        ) {
            type = parser.next()
        }
        if (type == org.xmlpull.v1.XmlPullParser.START_TAG) {
            Drawable.createFromXml(appContext.resources, parser)
        } else null
    } catch (e: Exception) {
        throw e
    }

    // ---------- 内部工具 ----------

    private suspend fun parseApkInternal(uri: Uri, name: String, size: Long, keepBgTemplate: Boolean) {
        _ui.update { it.copy(parsingApk = true, parseError = null) }
        try {
            withContext(Dispatchers.IO) {
                copyUriToFile(uri, cacheApk)
                val info = patcher.loadInfo(cacheApk)
                if (!info.packageName.equals(UiState.EXPECTED_APK_PACKAGE, ignoreCase = true)) {
                    _ui.update {
                        it.copy(
                            apkInfo = null,
                            apk = null,
                            parseError = "APK 包名为 ${info.packageName ?: "未知"}，需要 ${UiState.EXPECTED_APK_PACKAGE}。" +
                                "请从 /system/app/SimpleIconThemeRes/ 提取 SimpleIconThemeRes.apk 后重新选择。",
                        )
                    }
                    return@withContext
                }
                _ui.update { st ->
                    val bg = if (keepBgTemplate) st.apk?.bgTemplatePath else null
                    st.copy(
                        apkInfo = info,
                        apk = (st.apk ?: TargetApk(uri.toString(), name, size, null))
                            .copy(bgTemplatePath = bg ?: info.bgTemplates.firstOrNull()),
                    )
                }
            }
        } catch (e: Exception) {
            appendLog("解析 APK 失败：${e.message}")
            _ui.update {
                it.copy(apk = null, parseError = "解析失败：${e.message}，请重新选择有效的 APK")
            }
        } finally {
            _ui.update { it.copy(parsingApk = false) }
        }
    }

    private fun appendLog(line: String) {
        _ui.update { st ->
            val logs = st.logs + line
            // 上限保护：超限时丢弃最旧的行
            st.copy(logs = if (logs.size > MAX_LOG_LINES) logs.takeLast(MAX_LOG_LINES) else logs)
        }
    }

    private fun takePersistable(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // 某些提供方不支持持久授权，忽略
        }
    }

    private fun uriReadable(uri: Uri): Boolean = try {
        appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    private fun copyUriToFile(uri: Uri, dest: File) {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取所选文件")
    }

    private fun readUriBytes(uri: Uri): ByteArray =
        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取 $uri")

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "unknown"
        var size = 0L
        try {
            appContext.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    size = if (c.isNull(1)) 0L else c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        return name to size
    }

    companion object {
        /** 下载目录下的输出子目录（相对 Downloads） */
        const val ENV_SUBDIR = "Download/vivoicons_patched"
        private const val MAX_LOG_LINES = 500
    }
}
