package com.melodee.autoplayer.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel : ViewModel() {
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        _isDarkTheme.value = prefs.getBoolean("is_dark_theme", false)
    }

    fun toggleTheme(context: Context) {
        viewModelScope.launch {
            _isDarkTheme.value = !_isDarkTheme.value
            context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_dark_theme", _isDarkTheme.value)
                .apply()
        }
    }
} 