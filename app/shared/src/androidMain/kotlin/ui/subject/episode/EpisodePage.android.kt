package com.wynime.app.ui.subject.episode

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.findActivity

@Composable
actual fun DisplayModeEffect(config: VideoScaffoldConfig) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val modeId = context.getPreferredDisplayModeId()
        context.setPreferredDisplayMode(config.displayModeId)
        onDispose {
            context.setPreferredDisplayMode(modeId)
        }
    }
}

private fun Context.getPreferredDisplayModeId(): Int {
    val activity = this.findActivity() ?: return 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val params = activity.window.attributes
        return params.preferredDisplayModeId
    }
    return 0
}

private fun Context.setPreferredDisplayMode(modeId: Int) {
    val activity = this.findActivity() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val params = activity.window.attributes
        if (modeId == 0) {
            params.preferredRefreshRate = 0f
            params.preferredDisplayModeId = 0
            activity.window.setAttributes(params)
        } else {
            if (display.supportedModes.orEmpty().any { it.modeId == modeId }) {
                params.preferredRefreshRate = display.supportedModes.first { it.modeId == modeId }.refreshRate
                params.preferredDisplayModeId = modeId
                activity.window.setAttributes(params)
            }
        }
    } else {

    }
}
