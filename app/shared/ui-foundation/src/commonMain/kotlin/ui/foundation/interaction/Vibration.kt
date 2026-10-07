package com.wynime.app.ui.foundation.interaction

import com.wynime.app.platform.Context

expect fun Context.vibrateIfSupported(strength: VibrationStrength = VibrationStrength.CLICK)

enum class VibrationStrength {
    TICK,
    CLICK,
    HEAVY_CLICK,
}
