package com.wynime.android.navigation

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.wynime.app.navigation.BrowserNavigator
import com.wynime.app.navigation.OpenBrowserResult
import com.wynime.app.navigation.QQ_GROUP_JOIN_LINK
import com.wynime.app.platform.Context
import com.wynime.utils.logging.logger

class AndroidBrowserNavigator : BrowserNavigator {
    private val logger = logger<AndroidBrowserNavigator>()

    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        val lastEx: Exception

        try {
            launchChromeTab(context, url)
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            lastEx = ex
        }

        try {
            view(url, context)
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            val finalEx = ex.apply { addSuppressed(lastEx) }
            logger.warn("Failed to open browser", finalEx)
            return OpenBrowserResult.Failure(finalEx, url)
        }
    }

    private fun launchChromeTab(context: Context, url: String) {
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, url.toUri())
    }

    override fun intentActionView(context: Context, url: String): OpenBrowserResult {
        try {
            view(url, context)
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            logger.warn("Failed to open browser", ex)
            return OpenBrowserResult.Failure(ex, url)
        }
    }

    override fun intentOpenVideo(context: Context, url: String): OpenBrowserResult {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW)
                .apply { setDataAndType(url.toUri(), "video/*") }
            context.startActivity(Intent.createChooser(browserIntent, "选择播放器"))
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            logger.warn("Failed to open video", ex)
            return OpenBrowserResult.Failure(ex, url)
        }
    }

    private fun view(url: String, context: Context) {
        val browserIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(url.toUri())
        }
        context.startActivity(browserIntent)
    }

    override fun openJoinGroup(context: Context): OpenBrowserResult {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW)
                .apply { setData(QQ_GROUP.toUri()) }
            context.startActivity(browserIntent)
            return OpenBrowserResult.Success
        } catch (ex: Exception) {
            logger.warn("Failed to open QQ", ex)
            return OpenBrowserResult.Failure(ex, QQ_GROUP_JOIN_LINK)
        }
    }
}

private const val QQ_GROUP =
    "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + "oiWgOz87g6x4Eskej1Ja0bKWYyZR_dPO"
 