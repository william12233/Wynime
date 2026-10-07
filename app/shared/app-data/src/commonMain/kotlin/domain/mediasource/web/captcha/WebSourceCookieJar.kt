package com.wynime.app.domain.mediasource.web.captcha

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import com.wynime.app.domain.mediasource.web.normalizedSessionHost
import com.wynime.utils.platform.currentTimeMillis

class WebSourceCookieJar : CookiesStorage {
    private class StoredCookie(
        val name: String,
        val value: String,

        val domain: String,

        val hostOnly: Boolean,
        val path: String,
        val expiresEpochMillis: Long?,
        val secure: Boolean,
        val httpOnly: Boolean,
    )

    private val lock = SynchronizedObject()
    private val cookies = mutableListOf<StoredCookie>()

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val host = requestUrl.host.normalizeHost() ?: return emptyList()
        val isSecure = requestUrl.protocol.name == "https"
        val path = requestUrl.encodedPath.ifBlank { "/" }
        return synchronized(lock) {
            evictExpiredLocked()
            cookies.filter { it.matches(host, path, isSecure) }
                .map { it.toKtorCookie() }
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val requestHost = requestUrl.host.normalizeHost() ?: return
        addCookieImpl(requestHost, cookie.domain, cookie.name, cookie.value, cookie.path, run {
            cookie.expires?.timestamp ?: cookie.maxAge?.let { currentTimeMillis() + it * 1000L }
        }, cookie.secure, cookie.httpOnly)
    }

    override fun close() {
    }

    fun addBrowserCookies(pageUrl: String, browserCookies: List<BrowserCookie>) {
        val pageHost = normalizedSessionHost(pageUrl) ?: return
        for (c in browserCookies) {
            if (c.name.isBlank()) continue
            addCookieImpl(
                requestHost = pageHost,
                domainAttribute = c.domain,
                name = c.name,
                value = c.value,
                path = c.path,
                expiresEpochMillis = c.expiresEpochMillis,
                secure = c.secure,
                httpOnly = c.httpOnly,
            )
        }
    }

    fun getCookieHeaderValues(url: String): List<String> {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return emptyList()
        val host = parsed.host.normalizeHost() ?: return emptyList()
        val isSecure = parsed.protocol.name == "https"
        val path = parsed.encodedPath.ifBlank { "/" }
        return synchronized(lock) {
            evictExpiredLocked()
            cookies.filter { it.matches(host, path, isSecure) }
                .map { "${it.name}=${it.value}" }
        }
    }

    fun clearForHost(host: String) {
        val normalized = host.normalizeHost() ?: return
        synchronized(lock) {
            cookies.removeAll { it.domain == normalized || it.domain.endsWith(".$normalized") }
        }
    }

    private fun addCookieImpl(
        requestHost: String,
        domainAttribute: String?,
        name: String,
        value: String,
        path: String?,
        expiresEpochMillis: Long?,
        secure: Boolean,
        httpOnly: Boolean,
    ) {
        val domainNormalized = domainAttribute?.normalizeHost()
        val hostOnly = domainNormalized == null
        val effectiveDomain = domainNormalized ?: requestHost
        val stored = StoredCookie(
            name = name,
            value = value,
            domain = effectiveDomain,
            hostOnly = hostOnly,
            path = path?.ifBlank { null } ?: "/",
            expiresEpochMillis = expiresEpochMillis,
            secure = secure,
            httpOnly = httpOnly,
        )
        synchronized(lock) {
            cookies.removeAll { it.name == name && it.domain == effectiveDomain && it.path == stored.path }

            if (expiresEpochMillis == null || expiresEpochMillis > currentTimeMillis()) {
                cookies.add(stored)
            }
        }
    }

    private fun StoredCookie.matches(host: String, requestPath: String, isSecure: Boolean): Boolean {
        if (secure && !isSecure) return false
        val domainMatches = if (hostOnly) {

            host == domain
        } else {
            host == domain || host.endsWith(".$domain")
        }
        if (!domainMatches) return false
        return requestPath == path ||
                (requestPath.startsWith(path) && (path.endsWith("/") || requestPath.getOrNull(path.length) == '/'))
    }

    private fun StoredCookie.toKtorCookie(): Cookie = Cookie(
        name = name,
        value = value,
        domain = if (hostOnly) null else domain,
        path = path,
        expires = expiresEpochMillis?.let { GMTDate(it) },
        secure = secure,
        httpOnly = httpOnly,
    )

    private fun evictExpiredLocked() {
        val now = currentTimeMillis()
        cookies.removeAll { it.expiresEpochMillis != null && it.expiresEpochMillis <= now }
    }

    private fun String.normalizeHost(): String? {
        return lowercase().removePrefix(".").removePrefix("www.").takeIf { it.isNotBlank() }
    }
}

class WebSourceIdentityRegistry {
    private val lock = SynchronizedObject()
    private val userAgents = mutableMapOf<String, String>()

    fun setUserAgent(host: String, userAgent: String) {
        val normalized = normalizedSessionHost("https://$host") ?: return
        if (userAgent.isBlank()) return
        synchronized(lock) {
            userAgents[normalized] = userAgent
        }
    }

    fun userAgentFor(host: String): String? {
        val normalized = normalizedSessionHost("https://$host") ?: return null
        return synchronized(lock) {
            var candidate: String? = normalized
            while (candidate != null) {
                userAgents[candidate]?.let { return@synchronized it }
                candidate = candidate.substringAfter('.', "").takeIf { it.contains('.') }
            }
            null
        }
    }
}
