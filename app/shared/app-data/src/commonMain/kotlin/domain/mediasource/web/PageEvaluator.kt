/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Html
import kotlin.time.Duration

/**
 * 一次页面加载的结果, 可能来自直连 HTTP, 也可能来自浏览器 ([me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser]).
 */
data class LoadedPage(
    val finalUrl: String,
    val html: String,
    /**
     * HTTP 状态码. 浏览器加载时不可得, 为 `null`.
     */
    val status: Int? = null,
    /**
     * HTTP `Retry-After`. 仅在直连 HTTP 且响应携带时非 `null`.
     */
    val retryAfter: Duration? = null,
)

/**
 * 页面验证码流程的通用判定期望。
 *
 * 搜尋、詳情與影片解析由來源插件自行處理；Host 只需要知道頁面是否已通過阻擋檢查。
 */
sealed interface PageExpectation<out T> {
    data object AnyContent : PageExpectation<Document>
}

/**
 * [PageEvaluator] 对一个页面的判决.
 */
sealed interface PageVerdict<out T> {
    /** 解析成功. */
    data class Ok<T>(val value: T, val document: Document) : PageVerdict<T>

    /**
     * 正常页面, 合法的 "无结果".
     * @param document 解析出的 DOM (若 html 可解析), 供调试工具用不同配置重新解析.
     */
    data class EmptyContent(val document: Document?) : PageVerdict<Nothing>

    /** 页面被挡 (验证码 / 限流 / 404 / 403). */
    data class Blocked(val reason: BlockReason) : PageVerdict<Nothing>
}

sealed interface BlockReason {
    data class Captcha(val kind: WebCaptchaKind) : BlockReason

    /** HTTP 429 或站内冷却页. */
    data class RateLimited(val retryAfter: Duration?) : BlockReason

    data object NotFound : BlockReason

    /** 没有验证码特征的 HTTP 403. */
    data class Forbidden(val status: Int) : BlockReason
}

/**
 * 描述一次需要解决的验证码, 由 [BlockReason.Captcha] 产生.
 *
 * @see me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager.solve
 */
data class SolveRequest(
    val mediaSourceId: String,
    val pageUrl: String,
    val kind: WebCaptchaKind,
    /**
     * solve 成功与否的判定期望: 浏览器里的页面能以此期望解析出内容, 才算解决成功.
     */
    val expectation: PageExpectation<*>,
)

/**
 * 页面被挡时上抛的异常. `MediaFetcher` 按 [reason] 映射为对应的 fetch 状态.
 */
class BlockedException(
    val reason: BlockReason,
    val request: SolveRequest,
) : Exception("Blocked ($reason) @ ${request.pageUrl}")

/**
 * 所有通用 Web 会话都使用的唯一页面判定函数。
 *
 * 来源插件负责自己的业务解析，Host 只负责识别 HTTP 错误、冷却页与验证码，并为播放器/下载器保留浏览器会话。
 */
class PageEvaluator {
    fun <T> evaluate(page: LoadedPage, expectation: PageExpectation<T>): PageVerdict<T> {
        // 1. 404
        if (page.status == 404) {
            return PageVerdict.Blocked(BlockReason.NotFound)
        }

        val document = runCatching { Html.parse(page.html) }.getOrNull()

        // 2. 站内冷却页
        if (document != null && document.isSearchCooldownPage()) {
            return PageVerdict.Blocked(BlockReason.RateLimited(retryAfter = null))
        }

        // 3. HTTP 429
        if (page.status == 429) {
            return PageVerdict.Blocked(BlockReason.RateLimited(page.retryAfter))
        }

        // 4. 启发式检测
        WebCaptchaDetector.detect(page.finalUrl, page.html)?.let { kind ->
            return PageVerdict.Blocked(BlockReason.Captcha(kind))
        }

        // 5. 无特征的被挡状态码
        when (page.status) {
            468 -> return PageVerdict.Blocked(BlockReason.Captcha(WebCaptchaKind.Unknown))
            403 -> return PageVerdict.Blocked(BlockReason.Forbidden(403))
        }

        // 6. 兜底
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
