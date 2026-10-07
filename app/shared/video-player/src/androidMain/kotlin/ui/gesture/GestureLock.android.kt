package com.wynime.app.videoplayer.ui.gesture

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark

@PreviewLightDark
@Composable
private fun PreviewGestureLockLocked() {
    GestureLock(true, {})
}

@PreviewLightDark
@Composable
private fun PreviewGestureLockUnlocked() {
    GestureLock(false, {})
}
