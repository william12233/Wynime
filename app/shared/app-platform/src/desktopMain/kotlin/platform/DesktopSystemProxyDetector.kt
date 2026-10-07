package com.wynime.app.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.W32APIOptions
import io.ktor.http.Url
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger

sealed class DesktopSystemProxyDetector : SystemProxyDetector

class WindowsSystemProxyDetector : DesktopSystemProxyDetector() {
    private val logger = logger<WindowsSystemProxyDetector>()

    private val winHttp: Result<WinHttp> =
        runCatching {
            Native.load("winhttp", WinHttp::class.java, W32APIOptions.DEFAULT_OPTIONS) as WinHttp
        }

    override fun detect(): SystemProxyInfo? {
        val proxyConfig = getWindowsProxySettings()
            ?: return null
        var urlString = pointerToString(proxyConfig.lpszProxy) ?: return null
        if (urlString.startsWith("http=")) {

            urlString = urlString.removePrefix("http=").substringBefore(";")
        }

        val url = if (urlString.contains("://")) {
            Url(urlString)
        } else {
            @Suppress("HttpUrlsUsage")
            Url("http://$urlString")
        }

        logger.info { "Detected system proxy: $urlString" }
        return SystemProxyInfo(url)
    }

    @Suppress("ClassName", "SpellCheckingInspection", "unused")
    class WINHTTP_CURRENT_USER_IE_PROXY_CONFIG : Structure() {
        @JvmField
        var fAutoDetect: Boolean = false

        @JvmField
        var lpszAutoConfigUrl: Pointer? = null

        @JvmField
        var lpszProxy: Pointer? = null

        @JvmField
        var lpszProxyBypass: Pointer? = null

        override fun getFieldOrder(): List<String> {
            return listOf("fAutoDetect", "lpszAutoConfigUrl", "lpszProxy", "lpszProxyBypass")
        }
    }

    interface WinHttp : Library {
        @Suppress("FunctionName")
        fun WinHttpGetIEProxyConfigForCurrentUser(pProxyConfig: WINHTTP_CURRENT_USER_IE_PROXY_CONFIG): Boolean
    }

    private fun pointerToString(ptr: Pointer?): String? {
        return ptr?.getWideString(0)
    }

    private fun getWindowsProxySettings(): WINHTTP_CURRENT_USER_IE_PROXY_CONFIG? {
        val proxyConfig = WINHTTP_CURRENT_USER_IE_PROXY_CONFIG()
        val success = winHttp.getOrThrow().WinHttpGetIEProxyConfigForCurrentUser(proxyConfig)

        return if (success) proxyConfig else null
    }
}
