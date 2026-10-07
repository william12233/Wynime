package com.wynime.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import com.wynime.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.wynime.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry

val CookieJarFeature = ScopedHttpClientFeatureKey<WebSourceCookieJar?>("WebSourceCookieJar")

object CookieJarFeatureHandler : ScopedHttpClientFeatureHandler<WebSourceCookieJar?>(CookieJarFeature) {
    override fun applyToConfig(config: HttpClientConfig<*>, value: WebSourceCookieJar?) {
        value ?: return
        config.install(HttpCookies) {
            storage = value
        }
    }
}

val WebSourceIdentityFeature = ScopedHttpClientFeatureKey<WebSourceIdentityRegistry?>("WebSourceIdentity")

object WebSourceIdentityFeatureHandler :
    ScopedHttpClientFeatureHandler<WebSourceIdentityRegistry?>(WebSourceIdentityFeature) {
    override fun applyToClient(client: HttpClient, value: WebSourceIdentityRegistry?) {
        value ?: return
        client.plugin(HttpSend).intercept { request ->
            value.userAgentFor(request.url.host)?.let { ua ->
                request.headers[HttpHeaders.UserAgent] = ua
            }
            execute(request)
        }
    }
}

