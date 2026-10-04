/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class PageEvaluatorTest {
    private val evaluator = PageEvaluator()

    @Test
    fun `normal plugin page is accepted as content`() {
        val verdict = evaluator.evaluate(
            LoadedPage("https://example.com/play/1", "<html><body><video src='video.mp4'></video></body></html>"),
            PageExpectation.AnyContent,
        )
        assertIs<PageVerdict.Ok<*>>(verdict)
    }

    @Test
    fun `404 is not found`() {
        val verdict = evaluator.evaluate(
            LoadedPage("https://example.com/play/1", "not found", status = 404),
            PageExpectation.AnyContent,
        )
        assertEquals(BlockReason.NotFound, assertIs<PageVerdict.Blocked>(verdict).reason)
    }

    @Test
    fun `429 keeps retry after`() {
        val verdict = evaluator.evaluate(
            LoadedPage("https://example.com/play/1", "too many requests", status = 429, retryAfter = 12.seconds),
            PageExpectation.AnyContent,
        )
        val reason = assertIs<BlockReason.RateLimited>(assertIs<PageVerdict.Blocked>(verdict).reason)
        assertEquals(12.seconds, reason.retryAfter)
    }

    @Test
    fun `captcha page is classified before generic forbidden`() {
        val verdict = evaluator.evaluate(
            LoadedPage(
                "https://example.com/play/1",
                "<html><title>Just a moment...</title><div id='challenge-error-text'>Enable JavaScript and cookies to continue</div></html>",
                status = 403,
            ),
            PageExpectation.AnyContent,
        )
        assertEquals(
            BlockReason.Captcha(WebCaptchaKind.Cloudflare),
            assertIs<PageVerdict.Blocked>(verdict).reason,
        )
    }

    @Test
    fun `featureless forbidden page stays forbidden`() {
        val verdict = evaluator.evaluate(
            LoadedPage("https://example.com/play/1", "<html><body>Forbidden</body></html>", status = 403),
            PageExpectation.AnyContent,
        )
        assertEquals(BlockReason.Forbidden(403), assertIs<PageVerdict.Blocked>(verdict).reason)
    }

    @Test
    fun `cooldown page is rate limited`() {
        val verdict = evaluator.evaluate(
            LoadedPage(
                "https://example.com/search?q=re0",
                "<html><body><div class='msg-jump'>請不要頻繁操作，搜索時間間隔爲3秒</div></body></html>",
            ),
            PageExpectation.AnyContent,
        )
        assertIs<BlockReason.RateLimited>(assertIs<PageVerdict.Blocked>(verdict).reason)
    }
}
