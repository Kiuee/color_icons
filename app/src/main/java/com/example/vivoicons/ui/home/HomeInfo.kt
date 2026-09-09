package com.example.vivoicons.ui.home

import androidx.annotation.DrawableRes
import com.example.vivoicons.R

/**
 * 首页信息条目（代码级静态数据：在这里直接增删改内容即可）。
 * [imageRes] 可选：条目标题下方展示一张 drawable 配图（替换占位图改 res/drawable 下的文件即可）。
 */
data class HomeInfoItem(
    val title: String,
    val body: String,
    @DrawableRes val imageRes: Int? = null,
)

val HOME_INFO_ITEMS = listOf(
    HomeInfoItem("版本信息", "1.1 · 基于 ARSCLib 1.3.8 引擎"),
    HomeInfoItem(
        "mc / sc 说明",
        "mc 为图标主体层（下，绿点示意），sc 为叠加其上的效果层（上，橙点示意），两层最终叠加显示为完整图标。",
        imageRes = R.drawable.mc_sc_explainer,
    ),
    HomeInfoItem("使用说明", "选择目标 APK → 为各包名添加 mc/sc 资源 → 一次性注入。输出为未签名 APK，请使用外部签名工具签名后安装。"),
)
