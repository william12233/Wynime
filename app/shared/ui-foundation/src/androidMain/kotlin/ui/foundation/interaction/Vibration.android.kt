package com.wynime.app.ui.foundation.interaction

import android.annotation.SuppressLint
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import com.wynime.app.platform.Context

@RequiresPermission(android.Manifest.permission.VIBRATE)
actual fun Context.vibrateIfSupported(strength: VibrationStrength) {
    val effect = strength.toEffect() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService<VibratorManager>()?.vibrate(CombinedVibration.createParallel(effect))
    } else {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(effect)
        } else {

        }
    }
}

private fun VibrationStrength.toEffect(): VibrationEffect? {
    return when (this) {
        VibrationStrength.TICK -> defaultTick
        VibrationStrength.CLICK -> defaultClick
        VibrationStrength.HEAVY_CLICK -> defaultHeavyClick
    }
}

private val defaultTick: VibrationEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createPredefined(VibrationEffect.EFFECT_TICK)
    } else {
        null
    }
}

private val defaultClick: VibrationEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createPredefined(VibrationEffect.EFFECT_CLICK)
    } else {
        null
    }
}

private val defaultHeavyClick: VibrationEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    } else {
        null
    }
}

@SuppressLint("NewApi")
private fun createPredefined(int: Int): VibrationEffect {
    return VibrationEffect.createPredefined(int)
}