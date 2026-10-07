package com.wynime.app.ui.foundation.effects

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import com.wynime.app.platform.LocalContext

@Composable
actual fun DarkStatusBarAppearance() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val appearanceLightStatusBars = insetsController.isAppearanceLightStatusBars
        insetsController.isAppearanceLightStatusBars = false

        onDispose {
            insetsController.isAppearanceLightStatusBars = appearanceLightStatusBars
        }
    }
}