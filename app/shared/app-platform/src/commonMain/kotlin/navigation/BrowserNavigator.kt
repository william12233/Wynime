package com.wynime.app.navigation

import com.wynime.app.platform.Context

interface BrowserNavigator {
    fun openBrowser(context: Context, url: String): OpenBrowserResult

    fun openJoinGroup(context: Context): OpenBrowserResult

    fun openJoinTelegram(context: Context): OpenBrowserResult = openBrowser(
        context,
        "https://t.me/openani",
    )

    fun intentActionView(context: Context, url: String): OpenBrowserResult

    fun intentOpenVideo(context: Context, url: String): OpenBrowserResult {
        return OpenBrowserResult.Success
    }
}

const val QQ_GROUP_ID = "927170241"
const val QQ_GROUP_JOIN_LINK =
    "https://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=-6GULqAjYtA7HERBcFn9_Hz3789NUALP&authKey=Hsdzw9xBWcAaRKyt%2BmxYP%2FQElAPgOS0PY5pw2ld6YrN04YRY%2F6IWaVZn9CuhS7XR&noverify=0&group_code=927170241"

sealed class OpenBrowserResult {
    data object Success : OpenBrowserResult()

    data class Failure(val throwable: Throwable, val dest: String) : OpenBrowserResult()
}

object NoopBrowserNavigator : BrowserNavigator {
    override fun openBrowser(context: Context, url: String): OpenBrowserResult {
        return OpenBrowserResult.Success
    }

    override fun openJoinGroup(context: Context): OpenBrowserResult {
        return OpenBrowserResult.Success
    }

    override fun intentActionView(context: Context, url: String): OpenBrowserResult {
        return OpenBrowserResult.Success
    }
}
