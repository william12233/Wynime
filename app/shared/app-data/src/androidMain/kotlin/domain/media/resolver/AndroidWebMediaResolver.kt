package com.wynime.app.domain.media.resolver

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.platform.LocalContext
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import com.wynime.datasources.api.matcher.WebVideoMatcher
import com.wynime.datasources.api.matcher.WebVideoMatcherContext
import com.wynime.datasources.api.matcher.WebViewConfig
import com.wynime.datasources.api.matcher.videoOrNull
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentSkipListSet

class AndroidWebMediaResolver(
    private val matcherLoader: MediaSourceWebVideoMatcherLoader,
    private val settingsRepository: SettingsRepository,
    private val webSessionManager: WebSessionManager,
) : MediaResolver {
    private companion object {
        private val logger = logger<AndroidWebMediaResolver>()
    }

    private val matchersFromClasspath by lazy {
        java.util.ServiceLoader.load(WebVideoMatcher::class.java, this::class.java.classLoader).filterNotNull()
    }

    override fun supports(media: Media): Boolean = media.download is ResourceLocation.WebVideo

    private var attached: Context? = null

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun ComposeContent() {
        super.ComposeContent()

        val context = LocalContext.current
        DisposableEffect(true) {
            attached = context
            onDispose {
                attached = null
            }
        }
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        if (!supports(media)) throw UnsupportedMediaException(media)

        val matchersFromMediaSource = matcherLoader.loadMatchers(media.mediaSourceId)
        val allMatchers = matchersFromMediaSource + matchersFromClasspath

        val context = WebVideoMatcherContext(media)
        fun match(url: String): WebVideoMatcher.MatchResult? {
            return allMatchers
                .asSequence()
                .map { matcher ->
                    matcher.match(url, context)
                }
                .firstOrNull { it !is WebVideoMatcher.MatchResult.Continue }
        }

        val config = allMatchers.fold(WebViewConfig.Empty) { acc, matcher ->
            matcher.patchConfig(acc)
        }
        logger.info { "Final config: $config" }
        val timeoutMillis = settingsRepository.videoResolverSettings.flow.first().effectiveResourceExtractionTimeoutMillis

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
                timeoutMillis = timeoutMillis,
                resourceMatcher = resourceMatcher,
            ) ?: AndroidWebViewVideoExtractor(timeoutMillis).getVideoResourceUrl(
                attached ?: throw IllegalStateException("WebVideoSourceResolver not attached"),
                media.download.uri,
                config,
                resourceMatcher,
            )
            )?.let { resource ->
            allMatchers.firstNotNullOfOrNull { matcher ->
                matcher.match(resource.url, context).videoOrNull
            }
        } ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
        return HttpStreamingMediaDataProvider(
            webVideo.m3u8Url,
            media.originalTitle,
            webVideo.headers,
            media.extraFiles.toMediampMediaExtraFiles(),
        )
    }
}

class AndroidWebViewVideoExtractor(
    private val timeoutMillis: Long = WebViewVideoExtractor.DEFAULT_TIMEOUT,
) : WebViewVideoExtractor {
    private companion object {
        private val logger = logger<AndroidWebViewVideoExtractor>()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource? {

        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<WebResource>()
            val loadedNestedUrls = ConcurrentSkipListSet<String>()

            runCatching {
                for (string in config.cookies) {
                    CookieManager.getInstance().setCookie(pageUrl, string)
                }
            }.onFailure { exception ->
                logger.error("Failed to set cookie", exception)
            }

            fun handleUrl(webView: WebView, url: String): Boolean {
                val matched = resourceMatcher(url)
                when (matched) {
                    Instruction.Continue -> return false
                    Instruction.FoundResource -> {
                        deferred.complete(WebResource(url))
                        return true
                    }

                    Instruction.LoadPage -> {
                        logger.info { "WebView loading nested page: $url" }
                        launch(Dispatchers.Main) {
                            if (webView.url == url) return@launch
                            if (!loadedNestedUrls.add(url)) return@launch
                            logger.info { "WebView navigating to new url: $url" }
                            webView.loadUrl(url)

                        }
                        return false
                    }
                }
            }

            loadedNestedUrls.add(pageUrl)
            createWebView(context, deferred, ::handleUrl).loadUrl(pageUrl)

            try {
                withTimeoutOrNull(timeoutMillis) {
                    deferred.await()
                }
            } finally {
                deferred.cancel()
            }
        }

    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(DelicateCoroutinesApi::class)
    private fun createWebView(
        context: Context,
        deferred: CompletableDeferred<WebResource>,
        handleUrl: (WebView, String) -> Boolean,
    ): WebView = WebView(context).apply {
        val webView = this
        deferred.invokeOnCompletion {
            GlobalScope.launch(Dispatchers.Main.immediate) {
                webView.destroy()
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url ?: return super.shouldInterceptRequest(view, request)
                if (handleUrl(view, url.toString())) {
                    logger.info { "Found video resource via shouldInterceptRequest: $url" }

                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8", 500,
                        "Internal Server Error",
                        mapOf(),
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onLoadResource(view: WebView, url: String) {
                if (handleUrl(view, url)) {
                    logger.info { "Found video resource via onLoadResource: $url" }
                }
                super.onLoadResource(view, url)
            }
        }
    }
}
