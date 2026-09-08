package com.example.vivoicons

import com.example.vivoicons.engine.ApkPatcher
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证从 samples/target.apk 扫描已存在包名 */
class ScanPackagesTest {

    @Test
    fun loadInfo_scansExistingPackages() {
        val projectDir = File(System.getProperty("user.dir")).let { start ->
            generateSequence(start) { it.parentFile }
                .firstOrNull { File(it, "samples/target.apk").exists() } ?: start
        }
        val input = File(projectDir, "samples/target.apk")
        if (!input.exists()) {
            println("SKIP: samples/target.apk 不存在")
            return
        }
        val info = ApkPatcher().loadInfo(input)
        println("scanned: ${info.existingPackages}")
        assertTrue("应扫描出至少一个已有包名", info.existingPackages.isNotEmpty())
        // 背景模板 bubei_tingshu_b_s5_1x1_bg.xml 必然对应该包名
        assertTrue(
            "应包含 bubei.tingshu",
            info.existingPackages.any { it.equals("bubei.tingshu", ignoreCase = true) },
        )
        // 每个包名都应是点分格式
        info.existingPackages.forEach { pkg ->
            assertTrue("包名应包含点: $pkg", pkg.contains('.'))
        }
    }
}
