package com.example.vivoicons.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 外观与更新设置 */
data class AppSettings(
    /** true = 跟随系统主题色（Android 12+ 动态取色）；false = 使用手动选择的种子色 */
    val dynamicColor: Boolean = true,
    /** 手动主题色种子色（ARGB） */
    val seedColor: Int = 0xFF5655C4.toInt(),
    /** 深色模式：system / light / dark */
    val darkMode: String = DARK_MODE_SYSTEM,
    /** 自动检查更新 */
    val autoUpdate: Boolean = true,
) {
    companion object {
        const val DARK_MODE_SYSTEM = "system"
        const val DARK_MODE_LIGHT = "light"
        const val DARK_MODE_DARK = "dark"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val seedColor = intPreferencesKey("seed_color")
        val darkMode = stringPreferencesKey("dark_mode")
        val autoUpdate = booleanPreferencesKey("auto_update")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            dynamicColor = p[Keys.dynamicColor] ?: true,
            seedColor = p[Keys.seedColor] ?: 0xFF5655C4.toInt(),
            darkMode = p[Keys.darkMode] ?: AppSettings.DARK_MODE_SYSTEM,
            autoUpdate = p[Keys.autoUpdate] ?: true,
        )
    }

    suspend fun setDynamicColor(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.dynamicColor] = value }

    suspend fun setSeedColor(value: Int) =
        context.settingsDataStore.edit { it[Keys.seedColor] = value }

    suspend fun setDarkMode(value: String) =
        context.settingsDataStore.edit { it[Keys.darkMode] = value }

    suspend fun setAutoUpdate(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.autoUpdate] = value }
}
