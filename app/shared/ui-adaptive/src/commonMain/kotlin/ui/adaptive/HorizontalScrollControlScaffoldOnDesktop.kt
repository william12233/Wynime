package com.wynime.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wynime.app.ui.foundation.HorizontalScrollControlDefaults
import com.wynime.app.ui.foundation.HorizontalScrollControlScaffold
import com.wynime.app.ui.foundation.HorizontalScrollControlState
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.utils.platform.Platform

@Composable
fun HorizontalScrollControlScaffoldOnDesktop(
    state: HorizontalScrollControlState,
    modifier: Modifier = Modifier,
    scrollLeftButton: @Composable () -> Unit = {
        HorizontalScrollControlDefaults.ScrollLeftButton()
    },
    scrollRightButton: @Composable () -> Unit = {
        HorizontalScrollControlDefaults.ScrollRightButton()
    },
    content: @Composable () -> Unit
) {
    val currentPlatform = LocalPlatform.current

    if (currentPlatform is Platform.Desktop) {
        HorizontalScrollControlScaffold(
            state = state,
            modifier = modifier,
            scrollLeftButton = scrollLeftButton,
            scrollRightButton = scrollRightButton,
            content = content,
        )
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}