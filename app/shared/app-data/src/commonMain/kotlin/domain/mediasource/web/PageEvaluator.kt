package com.wynime.app.domain.mediasource.web

import com.wynime.utils.xml.Document
import com.wynime.utils.xml.Html
import kotlin.time.Duration

data class LoadedPage(
    val finalUrl: String,
    val html: String,

    val status: Int? = null,

    val retryAfter: Duration? = null,
)

sealed interface PageExpectation<out T> {
    data object AnyContent : PageExpectation<Document>
}

sealed interface PageVerdict<out T> {

    data class Ok<T>(val value: T, val document: Document) : PageVerdict<T>

    data class EmptyContent(val document: Document?) : PageVerdict<Nothing>

    data class Blocked(val reason: BlockReason) : PageVerdict<Nothing>
}

sealed interface BlockReason {
    data class Captcha(val kind: WebCaptchaKind) : BlockReason

    data class RateLimited(val retryAfter: Duration?) : BlockReason

    data object NotFound : BlockReason

    data class Forbidden(val status: Int) : BlockReason
}

data class SolveRequest(
    val mediaSourceId: String,
    val pageUrl: String,
    val kind: WebCaptchaKind,

    val expectation: PageExpectation<*>,
)

class BlockedException(
    val reason: BlockReason,
    val request: SolveRequest,
) : Exception("Blocked ($reason) @ ${request.pageUrl}")

class PageEvaluator {
    fun <T> evaluate(page: LoadedPage, expectation: PageExpectation<T>): PageVerdict<T> {

        if (page.status == 404) {
            return PageVerdict.Blocked(BlockReason.NotFound)
        }

        val document = runCatching { Html.parse(page.html) }.getOrNull()

        if (document != null && document.isSearchCooldownPage()) {
            return PageVerdict.Blocked(BlockReason.RateLimited(retryAfter = null))
        }

        if (page.status == 429) {
            return PageVerdict.Blocked(BlockReason.RateLimited(page.retryAfter))
        }

        WebCaptchaDetector.detect(page.finalUrl, page.html)?.let { kind ->
            return PageVerdict.Blocked(BlockReason.Captcha(kind))
        }

        when (page.status) {
            468 -> return PageVerdict.Blocked(BlockReason.Captcha(WebCaptchaKind.Unknown))
            403 -> return PageVerdict.Blocked(BlockReason.Forbidden(403))
        }

        if (expectation is PageExpectation.AnyContent && document != null && hasMeaningfulHtml(page.html)) {
            @Suppress("UNCHECKED_CAST")
            return PageVerdict.Ok(document, document) as PageVerdict<T>
        }
        return PageVerdict.EmptyContent(document)
    }

    private fun hasMeaningfulHtml(html: String): Boolean {
        val trimmed = html.trim()
        if (trimmed.isBlank()) return false
        return trimmed.contains("<html", ignoreCase = true) ||
                trimmed.contains("<body", ignoreCase = true) ||
                trimmed.length >= 128
    }
}
