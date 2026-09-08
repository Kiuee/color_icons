package com.example.vivoicons

import com.example.vivoicons.engine.ApkPatcher
import com.example.vivoicons.engine.PackageInjection
import com.reandroid.apk.ApkModule
import java.io.File
import org.junit.Test

/**
 * 诊断：用真实目标 APK（samples/target.apk）跑完整注入流程，
 * 产物写到 build/tmp/arsc_debug/ 供 aapt / zip 结构检查。
 * 手动运行：gradlew :app:testDebugUnitTest --tests "*ArscDiagnosticsTest*"
 */
class ArscDiagnosticsTest {

    @Test
    fun dumpPatchedRealApk() {
        val projectDir = File(System.getProperty("user.dir")).let { start ->
            generateSequence(start) { it.parentFile }.firstOrNull { File(it, "samples/target.apk").exists() || File(it, "app/build/outputs/apk/debug/app-debug.apk").exists() } ?: start
        }
        val input = sequenceOf(
            File(projectDir, "samples/target.apk"),
            File(projectDir, "app/build/outputs/apk/debug/app-debug.apk"),
        ).firstOrNull { it.exists() } ?: error("没有可用的输入 APK (user.dir=${System.getProperty("user.dir")})")

        val outDir = File(projectDir, "build/tmp/arsc_debug").apply { mkdirs() }
        val out = File(outDir, "out.apk")
        val log = StringBuilder()

        val module = ApkModule.loadApkFile(input)
        val bgTemplate = module.inputSources
            .map { it.name }
            .filter { it.endsWith("_b_s5_1x1_bg.xml") }
            .sorted()
            .firstOrNull()
            ?: module.inputSources.map { it.name }
                .first { it.startsWith("res/drawable") && it.endsWith(".xml") }

        val patcher = ApkPatcher(log = { log.appendLine(it) })
        val injections = listOf(
            PackageInjection("com.coolapk.market", MC_BYTES, SC_BYTES),
            PackageInjection("com.tencent.mm", MC_BYTES, null),
        )
        val report = patcher.patch(input, bgTemplate, injections, out)

        println("=== INPUT: ${input.absolutePath} (${input.length()} bytes), bgTemplate=$bgTemplate")
        println("=== LOG ===\n$log")
        println("=== verified=${report.verifiedResNames.size}, output=${out.absolutePath} (${out.length()} bytes)")
        input.copyTo(File(outDir, "original.apk"), overwrite = true)
    }

    companion object {
        private val MC_BYTES = "<vector name=\"mc\"/>".toByteArray()
        private val SC_BYTES = "<vector name=\"sc\"/>".toByteArray()
    }
}
