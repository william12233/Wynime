package com.wynime.app.domain.media.resolver

import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.util.date.toJvmDate
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.data.models.preference.VideoResolverSettings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.domain.settings.ProxyProvider
import com.wynime.app.platform.WynimeCefApp
import com.wynime.app.platform.Context
import com.wynime.app.platform.DesktopContext
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import com.wynime.datasources.api.matcher.WebVideoMatcher
import com.wynime.datasources.api.matcher.WebVideoMatcherContext
import com.wynime.datasources.api.matcher.WebViewConfig
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

class DesktopWebMediaResolver(
    private val context: DesktopContext,
    private val matcherLoader: MediaSourceWebVideoMatcherLoader,
    private val webSessionManager: WebSessionManager,
) : MediaResolver, KoinComponent {
    private companion object {
        private val logger = logger<DesktopWebMediaResolver>()
    }

    private val matchersFromClasspath by lazy {
        java.util.ServiceLoader.load(WebVideoMatcher::class.java).filterNotNull()
    }
    private val settings: SettingsRepository by inject()
    private val proxyProvider: ProxyProvider by inject()

    override fun supports(media: Media): Boolean = media.download is ResourceLocation.WebVideo

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        return withContext(Dispatchers.Default) {
            if (!supports(media)) throw UnsupportedMediaException(media)

            val resolverSettings = settings.videoResolverSettings.flow.first()
            val matchersFromMediaSource = matcherLoader.loadMatchers(media.mediaSourceId)
            val allMatchers = matchersFromMediaSource + matchersFromClasspath

            val webViewConfig = allMatchers.fold(WebViewConfig.Empty) { acc, matcher ->
                matcher.patchConfig(acc)
            }
            logger.info { "Final config: $webViewConfig" }

            val context = WebVideoMatcherContext(media)
            fun match(url: String): WebVideoMatcher.MatchResult? {
                return allMatchers
                    .asSequence()
                    .map { matcher ->
                        matcher.match(url, context)
                    }
                    .firstOrNull { it !is WebVideoMatcher.MatchResult.Continue }
            }

            val resourceMatcher = { url: String ->
                when (match(url)) {
                    WebVideoMatcher.MatchResult.Continue -> Instruction.Continue
                    WebVideoMatcher.MatchResult.LoadPage -> Instruction.LoadPage
                    is WebVideoMatcher.MatchResult.Matched -> Instruction.FoundResource
                    null -> Instruction.Continue
                }
            }

            val webVideo = (
                    webSessionManager.extractVideoResource(
                        pageUrl = media.download.uri,
                        timeoutMillis = resolverSettings.effectiveResourceExtractionTimeoutMillis,
                        resourceMatcher = resourceMatcher,
                    ) ?: CefVideoExtractor(proxyProvider.proxy.first(), resolverSettings)
                        .getVideoResourceUrl(
                            this@DesktopWebMediaResolver.context,
                            media.download.uri,
                            webViewConfig,
                            resourceMatcher = resourceMatcher,
                        )
                    )?.let {
                    (match(it.url) as? WebVideoMatcher.MatchResult.Matched)?.video
                } ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
            return@withContext HttpStreamingMediaDataProvider(
                webVideo.m3u8Url,
                media.originalTitle,
                webVideo.headers,
                media.extraFiles.toMediampMediaExtraFiles(),
            )
        }
    }
}

class CefVideoExtractor(
    private val proxyConfig: ProxyConfig?,
    private val videoResolverSettings: VideoResolverSettings,
) : WebViewVideoExtractor {
    private companion object {
        private val logger = logger<WebViewVideoExtractor>()
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction
    ): WebResource? = withContext(Dispatchers.IO) {
        var client: org.cef.CefClient? = null
        var browser: CefBrowser? = null
        val deferred = CompletableDeferred<WebResource>()

        try {
            val createdClient = WynimeCefApp.suspendCoroutineOnCefContext {
                WynimeCefApp.createClient()
            } ?: kotlin.run {
                logger.warn { "WynimeCefApp isn't initialized yet." }
                return@withContext null
            }
            client = createdClient

            val createdBrowser = WynimeCefApp.suspendCoroutineOnCefContext {
                val lastUrl = object {

                    var value: String? by atomic(null)
                }
                createdClient.createBrowser(
                    pageUrl,
                    CefRendering.DEFAULT,
                    true,
                    CefRequestContext.createContext { _, _, _, _, _, _, _ ->
                        object : CefResourceRequestHandlerAdapter() {
                            override fun onBeforeResourceLoad(
                                browser: CefBrowser?,
                                frame: CefFrame?,
                                request: CefRequest?
                            ): Boolean {
                                if (request != null && browser != null) {
                                    if (handleUrl(request, browser)) {
                                        return true
                                    }
                                }
                                return super.onBeforeResourceLoad(browser, frame, request)
                            }

                            private fun handleUrl(
                                request: CefRequest,
                                browser: CefBrowser
                            ): Boolean = synchronized(this) {
                                val url = request.url
                                val matched = resourceMatcher(url)
                                when (matched) {
                                    Instruction.Continue -> return false
                                    Instruction.FoundResource -> {
                                        deferred.complete(WebResource(url))
                                        logger.info { "Found video stream resource: $url" }
                                        return true
                                    }

                                    Instruction.LoadPage -> {
                                        if (browser.url == url || lastUrl.value == url) return false
                                        logger.info { "CEF loading nested page: $url, lastUrl=${lastUrl.value}" }
                                        lastUrl.value = url
                                        val escapedUrl = json.encodeToString(String.serializer(), url)
                                        WynimeCefApp.runOnCefContext {
                                            browser.executeJavaScript("window.location.href=$escapedUrl;", "", 1)
                                        }
                                        return true
                                    }
                                }
                            }
                        }
                    },
                )
            }
            browser = createdBrowser

            WynimeCefApp.suspendCoroutineOnCefContext {
                createdBrowser.setCloseAllowed()
                createdClient.addDisplayHandler(
                    object : CefDisplayHandlerAdapter() {
                        override fun onConsoleMessage(
                            browser: CefBrowser?,
                            level: CefSettings.LogSeverity?,
                            message: String?,
                            source: String?,
                            line: Int
                        ): Boolean {
                            logger.info { "CEF client console: ${message?.replace("\n", "\\n")} ($source:$line)" }
                            return super.onConsoleMessage(browser, level, message, source, line)
                        }
                    },
                )

                val cookieManager = CefCookieManager.getGlobalManager()
                val url = Url(pageUrl)
                for (cookie in config.cookies) {
                    val ktorCookie = parseServerSetCookieHeader(cookie)
                    cookieManager.setCookie(url.host, ktorCookie.toCefCookie())
                }

                logger.info { "Fetching $pageUrl" }

                createdBrowser.createImmediately()
            }

            withTimeoutOrNull(videoResolverSettings.effectiveResourceExtractionTimeoutMillis) {
                deferred.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to get video url." }
            if (deferred.isActive) {
                deferred.cancel()
            }
            null
        } finally {
            withContext(NonCancellable) {
                WynimeCefApp.closeBrowserAndDisposeClient(browser, client)
            }
            logger.info { "CEF client is disposed." }
        }
    }
}

private fun Cookie.toCefCookie() =
    CefCookie(
        name,
        value,
        domain,
        path,
        secure,
        httpOnly,
        null,
        null,
        expires != null,
        expires?.toJvmDate(),
    )
