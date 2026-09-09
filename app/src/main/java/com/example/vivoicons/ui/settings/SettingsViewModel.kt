package com.example.vivoicons.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vivoicons.BuildConfig
import com.example.vivoicons.data.AppSettings
import com.example.vivoicons.data.SettingsRepository
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 更新检查状态 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val version: String, val url: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = repo.settings.first()
            if (_settings.value.autoUpdate) {
                checkUpdate()
            }
        }
    }

    fun setDynamicColor(value: Boolean) = launchSet { repo.setDynamicColor(value) }
    fun setSeedColor(value: Int) = launchSet { repo.setSeedColor(value) }
    fun setDarkMode(value: String) = launchSet { repo.setDarkMode(value) }
    fun setAutoUpdate(value: Boolean) = launchSet { repo.setAutoUpdate(value) }

    private fun launchSet(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            _settings.value = repo.settings.first()
        }
    }

    /** 检查更新：依次尝试多个更新源（CDN 优先，API 兜底），第一个成功即返回 */
    fun checkUpdate() {
        if (_update.value is UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = withContext(Dispatchers.IO) { doCheck() }
        }
    }

    /** 更新源列表：(url, 是否 GitHub API 格式)。静态 update.json 走 CDN 无限额，规避 API 403 */
    private val updateSources: List<Pair<String, Boolean>> = listOf(
        "https://cdn.jsdelivr.net/gh/Kiuee/color_icons@main/update.json" to false,
        "https://raw.githubusercontent.com/Kiuee/color_icons/main/update.json" to false,
        "https://api.github.com/repos/Kiuee/color_icons/releases/latest" to true,
    )

    private fun doCheck(): UpdateState {
        val errors = mutableListOf<String>()
        for ((url, isApiFormat) in updateSources) {
            val result = fetchSource(url, isApiFormat)
            when (result) {
                is SourceHit -> {
                    return if (isNewerVersion(result.version, BuildConfig.VERSION_NAME)) {
                        UpdateState.Available(result.version, result.url)
                    } else {
                        UpdateState.Latest
                    }
                }
                is SourceFail -> errors += result.message
            }
        }
        return UpdateState.Error("所有更新源均不可达：" + errors.joinToString("；"))
    }

    private sealed interface SourceResult
    private data class SourceHit(val version: String, val url: String) : SourceResult
    private data class SourceFail(val message: String) : SourceResult

    private fun fetchSource(url: String, isApiFormat: Boolean): SourceResult {
        val connection = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", if (isApiFormat) "application/vnd.github+json" else "*/*")
            conn.setRequestProperty("User-Agent", "color-icons-app")
            conn
        } catch (e: Exception) {
            return SourceFail("$url 连接失败：${e.message}")
        }
        return try {
            val code = connection.responseCode
            if (code != 200) {
                return SourceFail("$url 返回 $code")
            }
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val json = JSONObject(body)
            if (isApiFormat) {
                val tag = json.optString("tag_name", "")
                val htmlUrl = json.optString("html_url", RELEASE_PAGE_URL)
                if (tag.isEmpty()) SourceFail("$url 返回异常（tag 为空）")
                else SourceHit(tag, htmlUrl)
            } else {
                val version = json.optString("versionName", "")
                val link = json.optString("url", RELEASE_PAGE_URL)
                if (version.isEmpty()) SourceFail("$url 返回异常（versionName 为空）")
                else SourceHit(version, link)
            }
        } catch (e: Exception) {
            SourceFail("$url 解析失败：${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val REPO_URL = "https://github.com/Kiuee/color_icons"
        private const val RELEASE_PAGE_URL = "https://github.com/Kiuee/color_icons/releases"

        /** 去 v 前缀，按「.」分段逐段比较数字（1.10 > 1.9） */
        fun isNewerVersion(remote: String, local: String): Boolean {
            fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
                .split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val a = parts(remote)
            val b = parts(local)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
