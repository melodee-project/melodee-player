package com.melodee.autoplayer.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentPalette = MutableStateFlow(getCurrentPalette())
    val currentPalette: StateFlow<ThemePalette> = _currentPalette.asStateFlow()
    
    private val _isDarkTheme = MutableStateFlow(getIsDarkTheme())
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    
    private val _isDynamicColorEnabled = MutableStateFlow(getIsDynamicColorEnabled())
    val isDynamicColorEnabled: StateFlow<Boolean> = _isDynamicColorEnabled.asStateFlow()
    
    fun setPalette(palette: ThemePalette) {
        _currentPalette.value = palette
        prefs.edit().putString(KEY_PALETTE, palette.name).apply()
    }
    
    fun setDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        prefs.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
    }
    
    fun setDynamicColorEnabled(enabled: Boolean) {
        _isDynamicColorEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
    
    fun toggleDarkTheme() {
        setDarkTheme(!_isDarkTheme.value)
    }
    
    fun toggleDynamicColor() {
        setDynamicColorEnabled(!_isDynamicColorEnabled.value)
    }
    
    private fun getCurrentPalette(): ThemePalette {
        val paletteName = prefs.getString(KEY_PALETTE, ThemePalette.DEFAULT.name)
        return try {
            ThemePalette.valueOf(paletteName ?: ThemePalette.DEFAULT.name)
        } catch (e: IllegalArgumentException) {
            ThemePalette.DEFAULT
        }
    }
    
    private fun getIsDarkTheme(): Boolean {
        return prefs.getBoolean(KEY_DARK_THEME, false)
    }
    
    private fun getIsDynamicColorEnabled(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    }
    
    companion object {
        private const val PREFS_NAME = "melodee_theme_prefs"
        private const val KEY_PALETTE = "theme_palette"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

// Composable helper to use the theme manager
@Composable
fun rememberThemeManager(context: Context): ThemeManager {
    return ThemeManager.getInstance(context)
}

// Theme state holder for Compose
data class ThemeState(
    val palette: ThemePalette,
    val isDarkTheme: Boolean,
    val isDynamicColorEnabled: Boolean
)

@Composable
fun rememberThemeState(themeManager: ThemeManager): ThemeState {
    val palette by themeManager.currentPalette.collectAsState()
    val isDarkTheme by themeManager.isDarkTheme.collectAsState()
    val isDynamicColorEnabled by themeManager.isDynamicColorEnabled.collectAsState()
    
    return ThemeState(
        palette = palette,
        isDarkTheme = isDarkTheme,
        isDynamicColorEnabled = isDynamicColorEnabled
    )
} 