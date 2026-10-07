package com.wynime.app.platform

import io.ktor.http.Url

data class SystemProxyInfo(
    val url: Url,
)

interface SystemProxyDetector {

    fun detect(): SystemProxyInfo?

    companion object {
        val instance by lazy { createSystemProxyDetector() }
    }
}

internal object NoOpSystemProxyDetector : SystemProxyDetector {
    override fun detect(): SystemProxyInfo? = null
}

internal expect fun createSystemProxyDetector(): SystemProxyDetector
