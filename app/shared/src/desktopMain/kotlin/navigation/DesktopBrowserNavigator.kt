package com.wynime.app.navigation

import com.wynime.app.platform.Context
import java.awt.Desktop
import java.net.URI

class DesktopBrowserNavigator : BrowserNavigator {
    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        try {
            Desktop.getDesktop().browse(URI.create(url))
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            return OpenBrowserResult.Failure(ex, url)
        }
    }

    override fun openJoinGroup(context: Context): OpenBrowserResult {
        return openBrowser(context, QQ_GROUP_JOIN_LINK)
    }

    override fun intentActionView(context: Context, url: String): OpenBrowserResult {
        return openBrowser(context, url)
    }
}