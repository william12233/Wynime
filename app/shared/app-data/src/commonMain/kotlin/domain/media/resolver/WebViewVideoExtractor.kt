package com.wynime.app.domain.media.resolver

import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.data.models.preference.VideoResolverSettings
import com.wynime.app.domain.media.resolver.WebViewVideoExtractor.Instruction
import com.wynime.app.platform.Context
import com.wynime.datasources.api.matcher.WebViewConfig
import com.wynime.utils.platform.annotations.TestOnly

interface WebViewVideoExtractor {
    sealed class Instruction {

        data object LoadPage : Instruction()

        data object FoundResource : Instruction()

        data object Continue : Instruction()
    }

    suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource?

    companion object {
        const val DEFAULT_TIMEOUT = 8_000L
    }
}

data class WebResource(
    val url: String
)

expect fun WebViewVideoExtractor(
    proxyConfig: ProxyConfig?,
    videoResolverSettings: VideoResolverSettings,
): WebViewVideoExtractor

@TestOnly
class TestWebViewVideoExtractor(
    private val urls: (pageUrl: String) -> List<String>,
) : WebViewVideoExtractor {
    override suspend fun getVideoResourceUrl(
        context: Context,
        pageUrl: String,
        config: WebViewConfig,
        resourceMatcher: (String) -> Instruction,
    ): WebResource {
        urls(pageUrl).forEach {
            if (resourceMatcher(it) is Instruction.FoundResource) {
                return WebResource(it)
            }
        }
        throw IllegalStateException("No match found")
    }
}
