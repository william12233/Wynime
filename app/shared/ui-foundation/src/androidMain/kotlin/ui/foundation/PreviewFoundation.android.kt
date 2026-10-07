package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wynime.app.platform.rememberPlatformWindow
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.utils.platform.annotations.TestOnly

@Composable
@TestOnly
@PublishedApi
internal actual inline fun ProvidePlatformCompositionLocalsForPreview(crossinline content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPlatformWindow provides rememberPlatformWindow(),
        content = { content() }
    )
}
