package com.wynime.app.ui.foundation.layout

import android.app.Activity
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wynime.app.platform.Context

@Suppress("USELESS_CAST")
actual suspend fun Context.setRequestFullScreen(window: PlatformWindowMP, fullscreen: Boolean) {
    android.util.Log.i("setRequestFullScreen", "Requesting fullscreen: $fullscreen, context=$this")
    if (this is Activity) {
        if (fullscreen) {

            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {

            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            this.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    } else {
        val orientation = if (fullscreen) {
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        } else {
            android.content.res.Configuration.ORIENTATION_PORTRAIT
        }
        resources.configuration.orientation = orientation
    }
}

actual fun Context.setSystemBarVisible(window: PlatformWindowMP, visible: Boolean) {
    if (this !is Activity) return

    val insetsController = WindowCompat.getInsetsController(this.window, this.window.decorView)
    val bitmask = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()

    if (visible) {
        insetsController.show(bitmask)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            this.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    } else {
        insetsController.hide(bitmask)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
}