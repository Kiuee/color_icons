package com.example.vivoicons.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder

private val Context.dataStore by preferencesDataStore(name = "vivoicons_prefs")

/**
 * 路径记忆仓库：持久化目标 APK、背景模板、当前步骤与整个待注入队列。
 * 注入完成按「清除记忆」后调用 [clear]。
 *
 * 队列序列化用最简单的转义文本格式（避免引入序列化插件）：
 * 记录间用 \u0001，字段间用 \u0002，每个字段都 URLEncoder 转义。
 */
class PrefsRepository(private val context: Context) {

    private val keyStep = intPreferencesKey("step")
    private val keyApkUri = stringPreferencesKey("apk_uri")
    private val keyApkName = stringPreferencesKey("apk_name")
    private val keyApkSize = longPreferencesKey("apk_size")
    private val keyBgTemplate = stringPreferencesKey("bg_template")
    private val keyQueue = stringPreferencesKey("queue")

    val state: Flow<SavedState> = context.dataStore.data.map { p ->
        SavedState(
            step = p[keyStep] ?: 1,
            apk = p[keyApkUri]?.let {
                TargetApk(it, p[keyApkName] ?: "", p[keyApkSize] ?: 0L, p[keyBgTemplate])
            },
            queue = decodeQueue(p[keyQueue]),
        )
    }

    suspend fun saveStep(step: Int) = context.dataStore.edit { it[keyStep] = step }

    suspend fun saveApk(apk: TargetApk?) = context.dataStore.edit { p ->
        if (apk == null) {
            p.remove(keyApkUri); p.remove(keyApkName); p.remove(keyApkSize); p.remove(keyBgTemplate)
        } else {
            p[keyApkUri] = apk.uri
            p[keyApkName] = apk.name
            p[keyApkSize] = apk.sizeBytes
            p[keyBgTemplate] = apk.bgTemplatePath ?: ""
        }
    }

    suspend fun saveBgTemplate(path: String?) = context.dataStore.edit {
        it[keyBgTemplate] = path ?: ""
    }

    suspend fun saveQueue(queue: List<PendingEntry>) = context.dataStore.edit {
        it[keyQueue] = encodeQueue(queue)
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }

    suspend fun readOnce(): SavedState = state.first()

    data class SavedState(
        val step: Int,
        val apk: TargetApk?,
        val queue: List<PendingEntry>,
    )

    companion object {
        private const val RECORD_SEP = '\u0001'
        private const val FIELD_SEP = '\u0002'

        fun encodeQueue(queue: List<PendingEntry>): String = queue.joinToString(RECORD_SEP.toString()) { e ->
            listOf(
                e.packageName,
                e.mcUri,
                e.mcName,
                e.scUri ?: "",
                e.scName ?: "",
            ).joinToString(FIELD_SEP.toString()) { urlEncode(it) }
        }

        fun decodeQueue(raw: String?): List<PendingEntry> {
            if (raw.isNullOrEmpty()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val f = record.split(FIELD_SEP).map { urlDecode(it) }
                if (f.size < 3 || f[0].isEmpty()) return@mapNotNull null
                PendingEntry(
                    packageName = f[0],
                    mcUri = f[1],
                    mcName = f[2],
                    scUri = f.getOrNull(3)?.takeIf { it.isNotEmpty() },
                    scName = f.getOrNull(4)?.takeIf { it.isNotEmpty() },
                )
            }
        }

        private fun urlEncode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())
        private fun urlDecode(s: String): String = try {
            URLDecoder.decode(s, Charsets.UTF_8.name())
        } catch (e: Exception) {
            s
        }
    }
}
