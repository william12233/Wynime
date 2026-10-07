package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import com.wynime.app.platform.DesktopContext
import com.wynime.app.platform.ExtraWindowProperties
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.PlatformWindow
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.currentPlatform
import java.io.File

@Composable
@TestOnly
@PublishedApi
internal actual inline fun ProvidePlatformCompositionLocalsForPreview(crossinline content: @Composable () -> Unit) {
    val windowState = remember { WindowState(size = DpSize(1920.dp, 1080.dp)) }
    CompositionLocalProvider(
        LocalContext provides remember {
            DesktopContext(
                windowState,
                File("."),
                File("."),
                File("./logs"),
                ExtraWindowProperties(),
            )
        },
        LocalPlatformWindow provides remember(windowState) {
            PlatformWindow(0L, null, windowState, currentPlatform())
        },
    ) {
        content()
    }
}
