package com.wynime.app.domain.mediasource.web.captcha

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.app.domain.mediasource.web.LoadedPage
import com.wynime.app.platform.WynimeCefApp
import com.wynime.utils.platform.currentPlatformDesktop
import com.wynime.utils.platform.Platform
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.callback.CefCookieVisitor
import org.cef.callback.CefStringVisitor
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

class CefCaptchaBrowser private constructor(
    private val client: CefClient,
    private val browser: CefBrowser,
    private val permit: WynimeCefApp.BrowserLifecyclePermit?,
) : CaptchaBrowser {
    private val _pageLoads = MutableSharedFlow<LoadedPage>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val pageLoads: SharedFlow<LoadedPage> get() = _pageLoads

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> get() = _isLoading

    private val capturedUserAgent = atomic<String?>(null)

    private val interceptor = atomic<((String) -> InterceptDecision)?>(null)

    override val userAgent: String
        get() = capturedUserAgent.value ?: fallbackUserAgent()

    override suspend fun navigate(url: String) {
        WynimeCefApp.runOnCefContext {
            browser.loadURL(url)
        }
    }

    override suspend fun currentPage(): LoadedPage? {
        return withTimeoutOrNull(2.seconds) {
            suspendCancellableCoroutine { cont ->
                WynimeCefApp.runOnCefContext {
                    val currentUrl = browser.url
                    if (currentUrl.isNullOrBlank()) {
                        cont.resume(null)
                        return@runOnCefContext
                    }
                    browser.getSource(
                        object : CefStringVisitor {
                            override fun visit(source: String?) {
                                if (cont.isActive) {
                                    cont.resume(LoadedPage(finalUrl = currentUrl, html = source.orEmpty()))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override suspend fun executeJavaScript(script: String) {
        WynimeCefApp.runOnCefContext {
            browser.executeJavaScript(script, browser.url, 0)
        }
    }

    override suspend fun collectCookies(urls: List<String>): List<BrowserCookie> {
        val result = mutableListOf<BrowserCookie>()
        for (url in urls) {
            result += collectCookiesForUrl(url)
        }
        return result
    }

    private suspend fun collectCookiesForUrl(url: String): List<BrowserCookie> {
        return withTimeoutOrNull(2.seconds) {
            suspendCancellableCoroutine { cont ->
                WynimeCefApp.runOnCefContext {
                    val cookies = mutableListOf<BrowserCookie>()
                    val visitor = object : CefCookieVisitor {
                        override fun visit(
                            cookie: org.cef.network.CefCookie?,
                            count: Int,
                            total: Int,
                            deleteCookie: BoolRef?,
                        ): Boolean {
                            cookie?.let {
                                cookies += BrowserCookie(
                                    name = it.name.orEmpty(),
                                    value = it.value.orEmpty(),
                                    domain = it.domain?.takeIf { d -> d.isNotBlank() },
                                    path = it.path?.takeIf { p -> p.isNotBlank() },
                                    expiresEpochMillis = if (it.hasExpires) it.expires?.time else null,
                                    secure = it.secure,
                                    httpOnly = it.httponly,
                                )
                            }
                            if (count + 1 >= total && cont.isActive) {
                                cont.resume(cookies.toList())
                            }
                            return count + 1 < total
                        }
                    }
                    val scheduled = CefCookieManager.getGlobalManager()
                        .visitUrlCookies(url, true, visitor)
                    if (!scheduled && cont.isActive) {
                        cont.resume(emptyList())
                    }
                }
            }
        } ?: emptyList()
    }

    override fun setResourceInterceptor(handler: ((String) -> InterceptDecision)?) {
        interceptor.value = handler
    }

    @Composable
    override fun View(modifier: Modifier) {
        SwingPanel(
            background = Color.Transparent,
            factory = { browser.uiComponent },
            modifier = modifier,
        )
    }

    override fun close() {
        try {
            WynimeCefApp.closeBrowserAndDisposeClientBlocking(browser, client)
        } finally {
            permit?.release()
        }
    }

    private fun installHandlers() {
        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    browser: CefBrowser?,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    if (browser == null) return
                    _isLoading.value = isLoading
                }

                override fun onLoadEnd(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    httpStatusCode: Int,
                ) {
                    if (browser == null || frame?.isMain != true) return
                    val finalUrl = browser.url.orEmpty()
                    browser.getSource(
                        object : CefStringVisitor {
                            override fun visit(source: String?) {
                                _pageLoads.tryEmit(LoadedPage(finalUrl = finalUrl, html = source.orEmpty()))
                            }
                        },
                    )
                }
            },
        )
        client.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun getResourceRequestHandler(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef?,
                ): CefResourceRequestHandlerAdapter {
                    return object : CefResourceRequestHandlerAdapter() {
                        override fun onBeforeResourceLoad(
                            browser: CefBrowser?,
                            frame: CefFrame?,
                            request: CefRequest?,
                        ): Boolean {
                            if (request != null) {
                                if (capturedUserAgent.value == null) {
                                    request.getHeaderByName("User-Agent")
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { capturedUserAgent.value = it }
                                }
                                val url = request.url
                                val handler = interceptor.value
                                if (url != null && handler != null) {
                                    if (handler(url) == InterceptDecision.Block) {
                                        return true
                                    }
                                }
                            }
                            return super.onBeforeResourceLoad(browser, frame, request)
                        }
                    }
                }
            },
        )
    }

    companion object {
        internal suspend fun create(): CefCaptchaBrowser {
            var permit: WynimeCefApp.BrowserLifecyclePermit? = WynimeCefApp.acquireDataSourceBrowserPermit()
            var client: CefClient? = null
            var browser: CefBrowser? = null
            try {
                return WynimeCefApp.suspendCoroutineOnCefContext {
                    val createdClient = WynimeCefApp.createClient()
                        ?: error("WynimeCefApp is not initialized, cannot create CaptchaBrowser")
                    client = createdClient

                    val instance = CefCaptchaBrowser(
                        client = createdClient,
                        browser = createdClient.createBrowser(
                            "about:blank",
                            CefRendering.DEFAULT,
                            true,
                            CefRequestContext.getGlobalContext(),
                        ).also { browser = it },
                        permit = permit,
                    )
                    permit = null
                    instance.installHandlers()
                    instance.browser.setCloseAllowed()
                    instance.browser.createImmediately()
                    instance
                }
            } catch (e: Throwable) {
                WynimeCefApp.closeBrowserAndDisposeClientBlocking(browser, client)
                permit?.release()
                throw e
            }
        }

        private fun fallbackUserAgent(): String {
            val osToken = "Windows NT 10.0; Win64; x64"
            return "Mozilla/5.0 ($osToken) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
    }
}

class DesktopCaptchaBrowserFactory : CaptchaBrowserFactory {
    override val isSupported: Boolean get() = true

    override suspend fun create(): CaptchaBrowser = CefCaptchaBrowser.create()
}
