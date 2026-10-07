package com.wynime.app.ui.foundation.effects

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.wynime.app.platform.LocalContext

@Composable
actual fun ScreenOnEffectImpl() {
    val activity =
        LocalContext.current as? Activity ?: androidx.lifecycle.compose.LocalLifecycleOwner.current as? Activity
    DisposableEffect(activity?.window) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}