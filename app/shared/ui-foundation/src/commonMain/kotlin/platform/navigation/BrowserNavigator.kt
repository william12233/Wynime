package com.wynime.app.platform.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalClipboard
import com.wynime.app.navigation.BrowserNavigator
import com.wynime.app.navigation.OpenBrowserResult
import com.wynime.app.platform.Context
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_browser_open_failed_copied
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import org.jetbrains.compose.resources.stringResource

val LocalBrowserNavigator: ProvidableCompositionLocal<BrowserNavigator> = staticCompositionLocalOf {
    error("No BrowserNavigator provided")
}

private val logger = logger<BrowserNavigator>()

@Composable
@Suppress("DEPRECATION")
fun rememberAsyncBrowserNavigator(): BrowserNavigator {
    val navigator = LocalBrowserNavigator.current
    val toaster = LocalToaster.current
    val clipboard = LocalClipboard.current
    val scope = rememberAsyncHandler()
    val openFailedCopiedText = stringResource(Lang.foundation_browser_open_failed_copied)

    val failureAction: suspend (OpenBrowserResult.Failure) -> Unit =
        remember(clipboard, toaster, openFailedCopiedText) {
        { failure ->
            clipboard.setClipEntryText(failure.dest)
            toaster.toast(openFailedCopiedText)
            logger.error(failure.throwable) { "Failed to open ${failure.dest}" }
        }
    }

    return remember(navigator) {
        object : BrowserNavigator {
            override fun openBrowser(context: Context, url: String): OpenBrowserResult {
                scope.launch {
                    val openResult = navigator.openBrowser(context, url)
                    if (openResult is OpenBrowserResult.Failure) {
                        failureAction(openResult)
                    }
                }
                return OpenBrowserResult.Success
            }

            override fun openJoinGroup(context: Context): OpenBrowserResult {
                scope.launch {
                    val openResult = navigator.openJoinGroup(context)
                    if (openResult is OpenBrowserResult.Failure) {
                        failureAction(openResult)
                    }
                }
                return OpenBrowserResult.Success
            }

            override fun intentActionView(context: Context, url: String): OpenBrowserResult {
                scope.launch {
                    val openResult = navigator.intentActionView(context, url)
                    if (openResult is OpenBrowserResult.Failure) {
                        failureAction(openResult)
                    }
                }
                return OpenBrowserResult.Success
            }
        }
    }
}
