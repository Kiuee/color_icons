package com.example.vivoicons

import com.example.vivoicons.engine.ApkPatcher
import com.example.vivoicons.engine.PackageInjection
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.TableBlock
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkPatcherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun buildFixtureApk(out: File) {
        val module = ApkModule()
        module.setTableBlock(TableBlock())
        val pkg = module.tableBlock.newPackage(0x7f, "com.example.theme")
        val bgPath = "res/drawable/com_example_theme_b_s5_1x1_bg.xml"
        pkg.getOrCreate("", "drawable", "com_example_theme_b_s5_1x1_bg")
            .setValueAsString(bgPath)
        module.add(ByteInputSource(BG_BYTES, bgPath))
        module.writeApk(out)
    }

    @Test
    fun loadInfo_findsBgTemplate() {
        val fixture = tmp.newFile("fixture.apk")
        buildFixtureApk(fixture)
        val logs = StringBuilder()
        val patcher = ApkPatcher(log = { logs.appendLine(it) })

        val info = patcher.loadInfo(fixture)

        assertEquals(1, info.bgTemplates.size)
        assertEquals("res/drawable/com_example_theme_b_s5_1x1_bg.xml", info.bgTemplates[0])
    }

    @Test
    fun patch_injectsMultiplePackages_andSelfVerifies() {
        val fixture = tmp.newFile("fixture.apk")
        buildFixtureApk(fixture)
        val output = tmp.newFile("out.apk")
        val patcher = ApkPatcher()

        val injections = listOf(
            PackageInjection("com.coolapk.market", MC_BYTES, SC_BYTES),
            PackageInjection("com.tencent.mm", MC_BYTES, null),
        )
        val info = patcher.loadInfo(fixture)
        val report = patcher.patch(fixture, info.bgTemplates[0], injections, output)

        // mc + sc + bg（coolapk）与 mc + bg（mm）共 5 个资源
        assertEquals(5, report.verifiedResNames.size)
        assertEquals(2, report.outcomes.size)
        assertTrue(report.outcomes.all { it.error == null })

        // 重新加载输出 APK，断言 arsc 条目与 zip 文件都存在且内容正确
        val module = ApkModule.loadApkFile(output)
        val pkg = module.tableBlock.listPackages().first()
        val expected = mapOf(
            "com_coolapk_market_b_s5_1x1_mc" to MC_BYTES,
            "com_coolapk_market_b_s5_1x1_sc" to SC_BYTES,
            "com_coolapk_market_b_s5_1x1_bg" to BG_BYTES,
            "com_tencent_mm_b_s5_1x1_mc" to MC_BYTES,
            "com_tencent_mm_b_s5_1x1_bg" to BG_BYTES,
        )
        for ((name, bytes) in expected) {
            val path = "res/drawable/$name.xml"
            val entry = pkg.getResource("drawable", name)?.get()
            assertNotNull("缺少 arsc 条目 $name", entry)
            assertEquals(path, entry!!.valueAsString)
            val source = module.getInputSource(path)
            assertNotNull("缺少 zip 条目 $path", source)
            assertTrue(
                "zip 内容不匹配 $name",
                source.openStream().use { it.readBytes() }.contentEquals(bytes),
            )
        }
    }

    companion object {
        private val BG_BYTES = "<vector name=\"bg\"/>".toByteArray()
        private val MC_BYTES = "<vector name=\"mc\"/>".toByteArray()
        private val SC_BYTES = "<vector name=\"sc\"/>".toByteArray()
    }
}
