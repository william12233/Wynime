package com.wynime.app.desktop

import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SystemThemeDetector {
    private val detector = OsThemeDetector.getDetector()

    private val _isDark = MutableStateFlow(detector.isDark)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    init {
        detector.registerListener {
            _isDark.value = it
        }
    }
}
