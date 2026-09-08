package com.example.vivoicons.data

import android.net.Uri

/** 缓存队列里的一组待注入资源（一个包名一组） */
data class PendingEntry(
    val packageName: String,
    /** SAF Uri 字符串 */
    val mcUri: String,
    val mcName: String,
    val scUri: String?,
    val scName: String?,
)

/** 选定的目标 APK */
data class TargetApk(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    /** 用户选定的背景模板 zip 路径 */
    val bgTemplatePath: String?,
)
