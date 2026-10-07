package com.wynime.app.domain.mediasource.web

import io.ktor.http.Url
import com.wynime.utils.xml.Document

enum class WebCaptchaKind {
    Image,
    Cloudflare,
    CloudflareTurnstile,
    SliderCaptcha,
    Unknown,
}

class WebPageCaptchaException(
    val url: String,
    val kind: WebCaptchaKind,
) : Exception("Captcha detected while loading $url: $kind")

object WebCaptchaDetector {
    fun detect(pageUrl: String, html: String): WebCaptchaKind? {
        val lowerHtml = html.lowercase()
        val lowerUrl = pageUrl.lowercase()

        if (
            "cf-turnstile" in lowerHtml ||
            "turnstile.render" in lowerHtml ||
            "challenges.cloudflare.com/turnstile" in lowerHtml
        ) {
            return WebCaptchaKind.CloudflareTurnstile
        }

        if (
            "puzzle-container" in lowerHtml
        ) {
            return WebCaptchaKind.SliderCaptcha
        }

        if (
            "需要输入验证码" in lowerHtml
        ) {
            return WebCaptchaKind.Unknown
        }

        val hasChallengeUrlMarker =
            "__cf_chl_" in lowerUrl ||
                "/cdn-cgi/challenge-platform/h/" in lowerUrl ||
                "/cdn-cgi/challenge-platform/orchestrate/" in lowerUrl
        val hasBlockingTitle =
            "<title>just a moment" in lowerHtml ||
                "checking your browser before accessing" in lowerHtml
        val hasBlockingText =
            "challenge-error-text" in lowerHtml ||
                "enable javascript and cookies to continue" in lowerHtml ||
                "cf-browser-verification" in lowerHtml ||
                "id=\"cf-challenge\"" in lowerHtml ||
                "id='cf-challenge'" in lowerHtml
        val hasChallengeScript =
            "window._cf_chl_opt" in lowerHtml ||
                "__cf_chl_" in lowerHtml ||
                "/cdn-cgi/challenge-platform/h/" in lowerHtml ||
                "/cdn-cgi/challenge-platform/orchestrate/" in lowerHtml

        if (
            hasChallengeUrlMarker ||
            hasBlockingText ||
            hasChallengeScript ||
            (hasBlockingTitle && (
                "challenge-platform" in lowerHtml ||
                    "cf-ray" in lowerHtml ||
                    "window.__cf\$cv\$params" in lowerHtml
                ))
        ) {
            return WebCaptchaKind.Cloudflare
        }

        val hasSafeLineChallenge =
            "/.safeline/static/favicon.png" in lowerHtml ||
                "id=\"slg-box\"" in lowerHtml ||
                "id=\"slg-title\"" in lowerHtml ||
                ("window.product_data" in lowerHtml && "slg-bg" in lowerHtml)
        if (hasSafeLineChallenge) {
            return WebCaptchaKind.Unknown
        }

        val hasInlineVerifyInput =
            "name=\"verify\"" in lowerHtml ||
                "name='verify'" in lowerHtml ||
                "placeholder=\"请输入验证码\"" in html ||
                "placeholder='请输入验证码'" in html ||
                "placeholder=\"請輸入驗證碼\"" in html ||
                "placeholder='請輸入驗證碼'" in html
        val hasInlineVerifyImage =
            "class=\"ds-verify-img\"" in lowerHtml ||
                "class='ds-verify-img'" in lowerHtml ||
                "/verify/index.html" in lowerHtml
        val hasInlineVerifySubmit =
            "class=\"verify-submit\"" in lowerHtml ||
                "class='verify-submit'" in lowerHtml ||
                "data-type=\"search\"" in lowerHtml ||
                "data-type='search'" in lowerHtml ||
                "提交驗證" in html ||
                "提交验证" in html

        if (
            hasInlineVerifyImage &&
            hasInlineVerifyInput &&
            hasInlineVerifySubmit
        ) {
            return WebCaptchaKind.Image
        }

        return null
    }
}

fun WebCaptchaKind.displayName(): String = when (this) {
    WebCaptchaKind.Image -> "图片验证码"
    WebCaptchaKind.Cloudflare -> "Cloudflare 验证"
    WebCaptchaKind.CloudflareTurnstile -> "Cloudflare Turnstile 验证"
    WebCaptchaKind.Unknown -> "验证码"
    WebCaptchaKind.SliderCaptcha -> "滑动验证"
}

internal fun normalizedSessionHost(pageUrl: String): String? {
    return runCatching { Url(pageUrl).host.lowercase() }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
}

internal fun normalizedStorageOrigin(pageUrl: String): String? {
    val url = runCatching { Url(pageUrl) }.getOrNull() ?: return null
    val host = url.host.lowercase().removePrefix("www.")
    if (host.isBlank()) return null
    val defaultPort = url.protocol.defaultPort
    val port = if (url.port == defaultPort) "" else ":${url.port}"
    return "${url.protocol.name}://$host$port"
}

internal fun Document.isSearchCooldownPage(): Boolean {
    val normalizedText = text()
        .replace(Regex("\\s+"), " ")
        .trim()
    val hasCooldownMessage = normalizedText.contains("请不要频繁操作") ||
        normalizedText.contains("請不要頻繁操作")
    val hasSearchIntervalMessage = normalizedText.contains("搜索时间间隔") ||
        normalizedText.contains("搜索時間間隔")
    val hasCooldownContainer = select(".msg-jump").isNotEmpty()
    val hasHistoryBackRedirect = select("a[href^=\"javascript:history.back\"]").isNotEmpty()
    return hasCooldownMessage &&
        hasSearchIntervalMessage &&
        (hasCooldownContainer || hasHistoryBackRedirect)
}
