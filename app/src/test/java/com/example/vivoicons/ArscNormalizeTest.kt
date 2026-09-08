package com.example.vivoicons

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.header.TypeHeader
import java.io.File
import org.junit.Test

/** 实验：检查各 TypeBlock 的 offset 编码类型；归一化为 OFFSET_32 后写盘供 aapt 验证 */
class ArscNormalizeTest {

    private fun projectDir(): File =
        File(System.getProperty("user.dir")).let { start ->
            generateSequence(start) { it.parentFile }
                .firstOrNull { File(it, "samples/target.apk").exists() } ?: start
        }

    private fun reportTypes(module: ApkModule, label: String): Int {
        var non32 = 0
        for (pkg in module.tableBlock.listPackages()) {
            for (pair in pkg.specTypePairArray) {
                for (tb in pair) {
                    val h = tb.headerBlock
                    val tag = when (h.offsetType) {
                        TypeHeader.OFFSET_SPARSE -> "SPARSE"
                        TypeHeader.OFFSET_16 -> "OFFSET16"
                        else -> "32"
                    }
                    if (h.offsetType != TypeHeader.OFFSET_32) non32++
                    println("[$label] type=${tb.typeName} id=${hTypeId(tb)} cfg=${tb.resConfig} offsetType=$tag entries=${h.countItem}")
                }
            }
        }
        return non32
    }

    private fun hTypeId(tb: com.reandroid.arsc.chunk.TypeBlock): Int = tb.id

    @Test
    fun normalizeAndDump() {
        val input = File(projectDir(), "samples/target.apk")
        val outDir = File(projectDir(), "build/tmp/arsc_debug").apply { mkdirs() }

        val module = ApkModule.loadApkFile(input)
        val non32 = reportTypes(module, "original")
        println("=== original non-32 type blocks: $non32")

        // 归一化
        for (pkg in module.tableBlock.listPackages()) {
            for (pair in pkg.specTypePairArray) {
                for (tb in pair) {
                    if (tb.headerBlock.offsetType != TypeHeader.OFFSET_32) {
                        tb.headerBlock.offsetType = TypeHeader.OFFSET_32
                    }
                }
            }
        }
        val normalized = File(outDir, "normalized.apk")
        module.writeApk(normalized)
        println("=== wrote ${normalized.absolutePath} (${normalized.length()} bytes)")
    }
}
