package com.wynime.app.domain.foundation

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.sse.SSE

val SseFeature = ScopedHttpClientFeatureKey<Boolean>("SSE")

data object SseFeatureHandler : ScopedHttpClientFeatureHandler<Boolean>(SseFeature) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: Boolean) {
        if (!value) return
        config.install(SSE)
    }
}
