package com.example.vivoicons.engine

import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.header.TypeHeader
import java.io.File

/** 目标 APK 的解析结果 */
data class ApkInfo(
    val packageName: String?,
    val versionName: String?,
    val versionCode: Int?,
    val sizeBytes: Long,
    /** APK 内可作为通用背景模板的 bg 文件完整 zip 路径，如 res/drawable/xxx_b_s5_1x1_bg.xml */
    val bgTemplates: List<String>,
    /** APK 中已存在的场景图标包名（由资源名还原，如 vitudio.android.camera360） */
    val existingPackages: List<String>,
)

/** 一个待注入的包资源组 */
class PackageInjection(
    val packageName: String,
    val mcBytes: ByteArray,
    val scBytes: ByteArray?,
) {
    val resPrefix: String get() = packageName.replace('.', '_')
}

/** 单个包的注入结果 */
data class InjectionOutcome(
    val packageName: String,
    val addedResNames: List<String> = emptyList(),
    val overwroteResNames: List<String> = emptyList(),
    val error: String? = null,
)

data class PatchReport(
    val outcomes: List<InjectionOutcome>,
    val outputApk: File,
    /** 自检通过的资源名 */
    val verifiedResNames: List<String>,
)

/**
 * 基于 ARSCLib 的资源注入引擎。
 * 向目标 APK 的 res/drawable 写入 <包名>_b_s5_1x1_{mc,sc,bg}.xml，
 * 并在 resources.arsc 中注册同名 drawable 资源；输出未签名 APK。
 */
class ApkPatcher(
    private val log: (String) -> Unit = {},
    private val progress: (Float, String) -> Unit = { _, _ -> },
) {

    fun loadInfo(apkFile: File): ApkInfo {
        log("正在解析 APK：${apkFile.name}")
        val module = ApkModule.loadApkFile(apkFile)
        val manifest = if (module.hasAndroidManifestBlock()) module.androidManifestBlock else null
        val bgTemplates = module.inputSources
            .map { it.name }
            .filter { it.endsWith("_b_s5_1x1_bg.xml") }
            .sorted()
        val existingPackages = scanExistingPackages(module)
        log("解析完成：包名=${module.packageName} 版本=${manifest?.versionName}")
        log("找到 ${bgTemplates.size} 个通用背景模板，${existingPackages.size} 个已存在的包名")
        return ApkInfo(
            packageName = module.packageName,
            versionName = manifest?.versionName,
            versionCode = module.versionCode.takeIf { it > 0 },
            sizeBytes = apkFile.length(),
            bgTemplates = bgTemplates,
            existingPackages = existingPackages,
        )
    }

    /**
     * 扫描 drawable 类型中已存在的场景图标资源名（xxx_b_s5_1x1_mc/sc/bg），
     * 还原出包名（下划线转点）并归并去重。
     */
    private fun scanExistingPackages(module: ApkModule): List<String> {
        val pkg = module.tableBlock?.listPackages()?.firstOrNull() ?: return emptyList()
        val found = LinkedHashMap<String, MutableSet<String>>()
        for (res in pkg.getResources()) {
            if (!res.type.equals("drawable", ignoreCase = true)) continue
            val name = res.name ?: continue
            val m = SCAN_REGEX.matchEntire(name) ?: continue
            found.getOrPut(m.groupValues[1].replace('_', '.')) { mutableSetOf() } += m.groupValues[2]
        }
        return found.keys.toList().sorted()
    }

    fun patch(
        apkFile: File,
        bgTemplatePath: String,
        injections: List<PackageInjection>,
        outFile: File,
    ): PatchReport {
        if (injections.isEmpty()) throw IllegalArgumentException("队列为空，没有可注入的包")
        log("加载 APK …")
        val module = ApkModule.loadApkFile(apkFile)
        val pkg = pickMainPackage(module)
        log("资源表包名：${pkg.name} (0x${Integer.toHexString(pkg.id)})")

        log("读取背景模板：$bgTemplatePath")
        val templateSource = module.getInputSource(bgTemplatePath)
            ?: throw IllegalArgumentException("APK 中不存在背景模板 $bgTemplatePath")
        val bgBytes = templateSource.openStream().use { it.readBytes() }

        val outcomes = mutableListOf<InjectionOutcome>()
        val total = injections.size
        injections.forEachIndexed { index, injection ->
            log("")
            log("[${index + 1}/$total] 处理包 ${injection.packageName}")
            outcomes += injectPackage(module, pkg, injection, bgBytes)
            progress((index + 1f) / total, "已处理 ${injection.packageName}")
        }

        log("")
        log("写入并压缩输出 APK …")
        progress(0f, "正在写出 APK")
        normalizeTypeBlocks(module)
        module.writeApk(outFile)
        progress(1f, "写出完成")
        log("输出：${outFile.absolutePath} (${outFile.length() / 1024} KB)")

        val verified = verify(outFile, outcomes)
        log("自检：${verified.size}/${outcomes.sumOf { it.addedResNames.size + it.overwroteResNames.size }} 个资源校验通过")
        return PatchReport(outcomes, outFile, verified)
    }

    private fun injectPackage(
        module: ApkModule,
        pkg: PackageBlock,
        injection: PackageInjection,
        bgBytes: ByteArray,
    ): InjectionOutcome {
        val layers = buildList {
            add("mc" to injection.mcBytes)
            injection.scBytes?.let { add("sc" to it) }
            add("bg" to bgBytes)
        }
        val added = mutableListOf<String>()
        val overwrote = mutableListOf<String>()
        for ((suffix, bytes) in layers) {
            val resName = "${injection.resPrefix}_b_s5_1x1_$suffix"
            val path = "res/drawable/$resName.xml"
            try {
                val existed = pkg.getResource("drawable", resName) != null ||
                    module.getInputSource(path) != null
                if (existed) {
                    log("  覆盖已存在资源：$resName")
                    module.removeInputSource(path)
                    overwrote += resName
                } else {
                    log("  新增资源：$resName")
                    added += resName
                }
                module.add(ByteInputSource(bytes, path))
                val entry = pkg.getOrCreate("", "drawable", resName)
                entry.setValueAsString(path)
            } catch (e: Exception) {
                log("  出错：$resName -> ${e.message}")
                return InjectionOutcome(
                    injection.packageName,
                    added,
                    overwrote,
                    "注入 $resName 失败：${e.message}",
                )
            }
        }
        return InjectionOutcome(injection.packageName, added, overwrote)
    }

    /** 输出文件自检：重新加载，确认每个资源都能解析到对应 zip 条目 */
    private fun verify(outFile: File, outcomes: List<InjectionOutcome>): List<String> {
        val verified = mutableListOf<String>()
        val module = ApkModule.loadApkFile(outFile)
        val pkg = pickMainPackage(module)
        for (outcome in outcomes) {
            val names = outcome.addedResNames + outcome.overwroteResNames
            for (name in names) {
                val path = "res/drawable/$name.xml"
                val entry = pkg.getResource("drawable", name)?.get()
                val valueOk = entry?.valueAsString == path
                val fileOk = module.getInputSource(path) != null
                if (valueOk && fileOk) {
                    verified += name
                } else {
                    log("自检失败：$name (entry=${entry?.valueAsString}, file=$fileOk)")
                }
            }
        }
        return verified
    }

    /**
     * 关键修复：新版 aapt2（compileSdk 33+，vivo 主题包即此类）默认把类型块写成
     * OFFSET16/SPARSE 编码；ARSCLib 追加 entry 后这些块的偏移不再符合紧凑编码规则，
     * Android 严格解析器会拒绝（症状：安装报“解析软件包时出现问题”，无法显示图标/包名）。
     * 写出前把所有类型块统一归一化为 OFFSET_32（等价 aapt2 --disable-sparse-encoding，
     * 体积略增但所有解析器都接受）。
     */
    private fun normalizeTypeBlocks(module: ApkModule) {
        var normalized = 0
        val table = module.tableBlock ?: return
        for (pkg in table.listPackages()) {
            for (pair in pkg.specTypePairArray) {
                for (tb in pair) {
                    if (tb.headerBlock.offsetType != TypeHeader.OFFSET_32) {
                        tb.headerBlock.offsetType = TypeHeader.OFFSET_32
                        normalized++
                    }
                }
            }
        }
        if (normalized > 0) log("已归一化 $normalized 个紧凑编码类型块（OFFSET16/SPARSE -> 32）")
    }

    private fun pickMainPackage(module: ApkModule): PackageBlock {
        val table = module.tableBlock
        return table.getPackageBlockById(MAIN_PACKAGE_ID)
            ?: table.listPackages().firstOrNull()
            ?: throw IllegalArgumentException("APK 没有 resources.arsc 资源表")
    }

    companion object {
        private const val MAIN_PACKAGE_ID = 0x7f

        /** 场景图标资源名：<包名下划线>_b_s5_1x1_<mc|sc|bg> */
        val SCAN_REGEX = Regex("^(.+)_b_s5_1x1_(mc|sc|bg)$", RegexOption.IGNORE_CASE)
    }
}
