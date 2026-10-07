package com.wynime.app.domain.media.resolver

import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.data.models.preference.VideoResolverSettings
import com.wynime.app.domain.media.resolver.WebViewVideoExtractor

actual fun WebViewVideoExtractor(
    proxyConfig: ProxyConfig?,
    videoResolverSettings: VideoResolverSettings,
): WebViewVideoExtractor {
    return AndroidWebViewVideoExtractor(
        videoResolverSettings.effectiveResourceExtractionTimeoutMillis,
    )
}
