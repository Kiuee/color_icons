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

    /** 检查更新：读取 GitHub Releases 最新版本并与本地版本比较 */
    fun checkUpdate() {
        if (_update.value is UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = withContext(Dispatchers.IO) { doCheck() }
        }
    }

    private fun doCheck(): UpdateState {
        val connection = try {
            val conn = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "color-icons-app")
            conn
        } catch (e: Exception) {
            return UpdateState.Error("无法连接更新服务器：${e.message}")
        }
        return try {
            val code = connection.responseCode
            if (code != 200) {
                return UpdateState.Error("服务器返回 $code（请确认仓库为 public）")
            }
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val url = json.optString("html_url", RELEASE_PAGE_URL)
            if (tag.isEmpty()) {
                UpdateState.Error("更新源返回异常（暂无 Release？）")
            } else if (isNewerVersion(tag, BuildConfig.VERSION_NAME)) {
                UpdateState.Available(tag, url)
            } else {
                UpdateState.Latest
            }
        } catch (e: Exception) {
            UpdateState.Error("检查失败：${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val REPO_URL = "https://github.com/Kiuee/color_icons"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Kiuee/color_icons/releases/latest"
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
