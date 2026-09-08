package com.example.vivoicons.ui.home

/** 首页信息条目（代码级静态数据：在这里直接增删改内容即可） */
data class HomeInfoItem(val title: String, val body: String)

val HOME_INFO_ITEMS = listOf(
    HomeInfoItem("版本信息", "1.0 · 基于 ARSCLib 1.3.8 引擎"),
    HomeInfoItem("使用说明", "选择目标 APK → 为各包名添加 mc/sc 资源 → 一次性注入。输出为未签名 APK，请使用外部签名工具签名后安装。"),
    HomeInfoItem("占位 1", ""),
    HomeInfoItem("占位 2", ""),
)
