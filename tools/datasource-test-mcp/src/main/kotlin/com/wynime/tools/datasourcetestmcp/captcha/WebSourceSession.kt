package com.wynime.tools.datasourcetestmcp.captcha

import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import com.wynime.app.domain.mediasource.web.BlockReason
import com.wynime.app.domain.mediasource.web.DesktopOnnxImageCaptchaRecognizer
import com.wynime.app.domain.mediasource.web.PageEvaluator
import com.wynime.app.domain.mediasource.web.PageExpectation
import com.wynime.app.domain.mediasource.web.PageVerdict
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.app.domain.mediasource.web.WebCaptchaKind
import com.wynime.app.domain.mediasource.web.captcha.BrowserImageCaptchaSolver
import com.wynime.app.domain.mediasource.web.captcha.CaptchaBrowser
import com.wynime.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import com.wynime.app.domain.mediasource.web.captcha.DesktopCaptchaBrowserFactory
import com.wynime.app.domain.mediasource.web.captcha.MacCmsImageCaptchaSolver
import com.wynime.app.domain.mediasource.web.captcha.SolveOutcome
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.wynime.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import com.wynime.tools.datasourcetestmcp.McpCefApp
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.xml.Document
import java.io.File
import kotlin.time.Duration

class WebSourceSession(
    val cookieJar: WebSourceCookieJar,
    val sessionManager: WebSessionManager,
) {

    constructor(
        client: ScopedHttpClient,
        cookieJar: WebSourceCookieJar,
        identityRegistry: WebSourceIdentityRegistry,
        backgroundScope: CoroutineScope,
        cefWorkDir: File = McpCefApp.defaultWorkDir(),
    ) : this(
        cookieJar,
        createDesktopWebSessionManager(client, cookieJar, identityRegistry, backgroundScope, cefWorkDir),
    )

    suspend fun <T> fetchPage(
        mediaSourceId: String,
        url: String,
        expectation: PageExpectation<T>,
        requestInterval: Duration,
    ): WebPageFetchResult<T> {
        var verdict = sessionManager.fetchPage(url, expectation)

        verdict.blockedBy<BlockReason.RateLimited>()?.let { rateLimited ->

            delay(rateLimited.retryAfter ?: requestInterval)
            verdict = sessionManager.fetchPage(url, expectation)
        }

        val captcha = verdict.blockedBy<BlockReason.Captcha>()
            ?: return WebPageFetchResult(url, verdict, autoSolvedCaptcha = null)

        val outcome = sessionManager.solve(
            SolveRequest(
                mediaSourceId = mediaSourceId,
                pageUrl = url,
                kind = captcha.kind,
                expectation = expectation,
            ),
            interactive = false,
        )
        if (outcome != SolveOutcome.Solved) {
            throw CaptchaUnsolvedException(url, captcha.kind, outcome)
        }

        verdict = sessionManager.fetchPage(url, expectation)
        verdict.blockedBy<BlockReason.Captcha>()?.let { stillBlocked ->

            throw CaptchaUnsolvedException(url, stillBlocked.kind, SolveOutcome.Failed(stillBlocked))
        }
        return WebPageFetchResult(url, verdict, autoSolvedCaptcha = captcha.kind)
    }

    suspend fun invalidate(url: String) {
        sessionHostOf(url)?.let { sessionManager.invalidate(it) }
    }
}

private fun createDesktopWebSessionManager(
    client: ScopedHttpClient,
    cookieJar: WebSourceCookieJar,
    identityRegistry: WebSourceIdentityRegistry,
    backgroundScope: CoroutineScope,
    cefWorkDir: File,
): WebSessionManager {
    val evaluator = PageEvaluator()
    val browserFactory = LazyCefCaptchaBrowserFactory(cefWorkDir)
    val recognizer = DesktopOnnxImageCaptchaRecognizer()
    return WebSessionManager(
        browserFactory = browserFactory,
        evaluator = evaluator,
        cookieJar = cookieJar,
        identityRegistry = identityRegistry,
        client = client,
        backgroundScope = backgroundScope,
        solvers = listOf(
            MacCmsImageCaptchaSolver(recognizer),
            BrowserImageCaptchaSolver(recognizer),
        ),
        maxSessions = browserFactory.recommendedMaxSessions,
    )
}

class WebPageFetchResult<T>(
    val url: String,
    val verdict: PageVerdict<T>,

    val autoSolvedCaptcha: WebCaptchaKind?,
) {

    val document: Document? get() = verdict.documentOrNull()

    val blockReason: BlockReason? get() = (verdict as? PageVerdict.Blocked)?.reason
}

class CaptchaUnsolvedException(
    val pageUrl: String,
    val kind: WebCaptchaKind,
    val outcome: SolveOutcome,
) : Exception("Captcha ($kind) at $pageUrl was not solved automatically: ${outcome.describe()}") {
    val host: String? = sessionHostOf(pageUrl)

    val summary: String
        get() = "${host ?: pageUrl} 开了人机验证 ($kind), 自动解决失败 (${outcome.describe()}), 已终止该数据源的流程"
}

fun BlockReason.describe(): String = when (this) {
    is BlockReason.Captcha -> "人机验证 ($kind)"
    is BlockReason.RateLimited -> "被限流" + (retryAfter?.let { " (Retry-After ${it.inWholeSeconds}s)" }.orEmpty())
    BlockReason.NotFound -> "404 Not Found"
    is BlockReason.Forbidden -> "HTTP $status Forbidden"
}

fun PageVerdict<*>.documentOrNull(): Document? = when (this) {
    is PageVerdict.Ok<*> -> document
    is PageVerdict.EmptyContent -> document
    is PageVerdict.Blocked -> null
}

internal fun sessionHostOf(url: String): String? = runCatching { Url(url).host }.getOrNull()
    ?.lowercase()
    ?.removePrefix("www.")
    ?.takeIf { it.isNotBlank() }

private fun SolveOutcome.describe(): String = when (this) {
    SolveOutcome.Solved -> "已解决"
    SolveOutcome.Cancelled -> "已取消"
    SolveOutcome.Unsupported -> "当前环境没有可用的浏览器"
    is SolveOutcome.Failed -> "所有自动策略均未通过" + (reason?.let { " (最后判定: ${it.describe()})" }.orEmpty())
}

private inline fun <reified R : BlockReason> PageVerdict<*>.blockedBy(): R? =
    (this as? PageVerdict.Blocked)?.reason as? R

private class LazyCefCaptchaBrowserFactory(
    private val workDir: File,
) : CaptchaBrowserFactory {
    private val delegate = DesktopCaptchaBrowserFactory()

    override val isSupported: Boolean get() = delegate.isSupported
    override val recommendedMaxSessions: Int get() = delegate.recommendedMaxSessions

    override suspend fun create(): CaptchaBrowser {
        McpCefApp.initialize(workDir)
        return delegate.create()
    }
}
