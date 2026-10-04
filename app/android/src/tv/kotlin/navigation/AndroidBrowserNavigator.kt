/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.navigation

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.OpenBrowserResult
import me.him188.ani.app.navigation.QQ_GROUP_JOIN_LINK
import me.him188.ani.app.platform.Context
import me.him188.ani.utils.logging.logger

/** Opens OAuth and external links using the TV browser or a regular VIEW intent. */
class AndroidBrowserNavigator : BrowserNavigator {
    private val logger = logger<AndroidBrowserNavigator>()

    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        return try {
            CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
            OpenBrowserResult.Success
        } catch (customTabsException: Exception) {
            try {
                view(context, url)
                OpenBrowserResult.Success
            } catch (viewException: Exception) {
                viewException.addSuppressed(customTabsException)
                logger.warn("Failed to open browser", viewException)
                OpenBrowserResult.Failure(viewException, url)
            }
        }
    }

    override fun openJoinGroup(context: Context): OpenBrowserResult = intentActionView(context, QQ_GROUP_JOIN_LINK)

    override fun intentActionView(context: Context, url: String): OpenBrowserResult = try {
        view(context, url)
        OpenBrowserResult.Success
    } catch (exception: Exception) {
        logger.warn("Failed to open external link", exception)
        OpenBrowserResult.Failure(exception, url)
    }

    private fun view(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = url.toUri() })
    }
}
