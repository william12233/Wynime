package com.wynime.utils.ktor

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.engine.http
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.serialization.Serializable

@Serializable
data class ClientProxyConfig(

    val url: String,

    val authorization: String? = null
)

object ClientProxyConfigValidator {
    fun parseProxy(url: String): ProxyConfig = if (url.startsWith("socks")) {
        Url(url).run {
            ProxyBuilder.socks(host, port)
        }
    } else {
        ProxyBuilder.http(url)
    }

    fun isValidProxy(url: String, allowSocks: Boolean = true): Boolean {
        return try {
            val u = URLBuilder(protocol = URLProtocol("dummy", 1)).takeFrom(url).build()
            if (u.host.isBlank()) return false
            if (!allowSocks && u.protocol.name in setOf("socks", "socks5")) return false
            if (u.protocol.name !in setOf("http", "https", "socks", "socks5")) return false
            true
        } catch (e: URLParserException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}

fun HttpClientConfig<*>.userAgent(
    value: String?
) {
    value ?: return
    install(UserAgent) {
        agent = value
    }
}

fun HttpClientConfig<*>.proxy(
    config: ClientProxyConfig?,
) {
    engine {
        this.setProxy(config)
    }

    config?.authorization?.let { authorization ->
        defaultRequest {
            header(HttpHeaders.ProxyAuthorization, authorization)
        }
    }
}

fun HttpClientEngineConfig.setProxy(
    config: ClientProxyConfig?,
) {
    proxy = if (config == null) {
        null
    } else {
        ClientProxyConfigValidator.parseProxy(config.url)
    }
}
