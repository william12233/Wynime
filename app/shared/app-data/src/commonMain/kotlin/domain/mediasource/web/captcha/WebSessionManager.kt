package com.wynime.app.domain.mediasource.web.captcha

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.domain.media.resolver.WebResource
import com.wynime.app.domain.media.resolver.WebViewVideoExtractor
import com.wynime.app.domain.mediasource.web.BlockReason
import com.wynime.app.domain.mediasource.web.LoadedPage
import com.wynime.app.domain.mediasource.web.PageEvaluator
import com.wynime.app.domain.mediasource.web.PageExpectation
import com.wynime.app.domain.mediasource.web.PageVerdict
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.app.domain.mediasource.web.normalizedSessionHost
import com.wynime.app.domain.mediasource.web.normalizedStorageOrigin
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class InteractiveSolveUi internal constructor(
    val request: SolveRequest,
    val browser: CaptchaBrowser,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val onRefresh: () -> Unit,
) {
    val title: String get() = normalizedSessionHost(request.pageUrl) ?: "验证码验证"
}

class WebSessionManager(
    private val browserFactory: CaptchaBrowserFactory,
    private val evaluator: PageEvaluator,
    val cookieJar: WebSourceCookieJar,
    private val identityRegistry: WebSourceIdentityRegistry,
    private val client: ScopedHttpClient,
    private val backgroundScope: CoroutineScope,
    private val solvers: List<CaptchaSolver> = emptyList(),
    private val solverEnabled: suspend () -> Boolean = { true },
    private val maxSessions: Int = 3,
    private val idleTtl: Duration = 5.minutes,
    private val stickyWindow: Duration = 60.seconds,
    private val solveFailCooldown: Duration = 60.seconds,
    private val browserLoadTimeout: Duration = 12.seconds,
    private val getTimeMillis: () -> Long = { currentTimeMillis() },
    private val ioContext: CoroutineContext = Dispatchers.IO_,
) {
    val isInteractiveSupported: Boolean get() = browserFactory.isSupported

    private class BrowserSession(
        val browser: CaptchaBrowser,
    ) {
        var lastUsedAtMillis: Long = 0
        var refCount: Int = 0
    }

    private class ActiveSolve(
        val interactive: Boolean,
    ) {
        val deferred = CompletableDeferred<SolveOutcome>()
        var job: Job? = null
    }

    private data class PendingSolvedPage(
        val requestKey: String,
        val page: LoadedPage,
        val storedAtMillis: Long,
    )

    private class HostState {
        var session: BrowserSession? = null
        var lastHttpBlockedAtMillis: Long = 0
        var lastSolveFailedAtMillis: Long = 0
        var lastSolveSucceededAtMillis: Long = 0
        var pendingSolvedPage: PendingSolvedPage? = null
        var activeSolve: ActiveSolve? = null
    }

    private val lock = Mutex()
    private val hostStates = mutableMapOf<String, HostState>()
    private val browserCreateSemaphore = Semaphore(MAX_CONCURRENT_BROWSER_CREATIONS)

    private fun hostStateLocked(host: String): HostState = hostStates.getOrPut(host) { HostState() }

    private val interactiveUiQueue = mutableListOf<InteractiveSolveUi>()
    private val _interactiveUi = MutableStateFlow<InteractiveSolveUi?>(null)

    val interactiveUi: StateFlow<InteractiveSolveUi?> get() = _interactiveUi

    private suspend fun publishUi(ui: InteractiveSolveUi) = lock.withLock {
        interactiveUiQueue.add(ui)
        _interactiveUi.value = interactiveUiQueue.first()
    }

    private suspend fun removeUi(ui: InteractiveSolveUi) = lock.withLock {
        interactiveUiQueue.remove(ui)
        _interactiveUi.value = interactiveUiQueue.firstOrNull()
    }

    init {
        backgroundScope.launch {
            while (true) {
                delay(SESSION_SWEEP_INTERVAL)
                sweepIdleSessions()
            }
        }
    }

    suspend fun <T> fetchPage(url: String, expectation: PageExpectation<T>): PageVerdict<T> {
        val host = normalizedSessionHost(url)

        if (host != null) {
            consumePendingSolvedPage(host, url)?.let { page ->
                return evaluator.evaluate(page, expectation)
            }
        }

        if (host != null && shouldStickToBrowser(host)) {
            loadInBrowser(host, url, expectation)?.let { verdict ->
                if (verdict is PageVerdict.Blocked && verdict.reason is BlockReason.Captcha) {
                    invalidate(host)
                }
                return verdict
            }
        }

        val page = httpFetch(url)
        val verdict = evaluator.evaluate(page, expectation)
        if (verdict !is PageVerdict.Blocked || verdict.reason !is BlockReason.Captcha || host == null) {
            return verdict
        }

        val now = getTimeMillis()
        val recentlySolved = lock.withLock {
            val state = hostStateLocked(host)
            state.lastHttpBlockedAtMillis = now
            state.lastSolveSucceededAtMillis > 0 &&
                    now - state.lastSolveSucceededAtMillis < stickyWindow.inWholeMilliseconds
        }

        val browserVerdict = loadInBrowser(host, url, expectation)
        if (browserVerdict != null) {
            if (browserVerdict is PageVerdict.Blocked && browserVerdict.reason is BlockReason.Captcha) {

                invalidate(host)
            }
            return browserVerdict
        }

        if (recentlySolved) {

            cookieJar.clearForHost(host)
        }
        return verdict
    }

    private suspend fun shouldStickToBrowser(host: String): Boolean = lock.withLock {
        val state = hostStates[host] ?: return@withLock false
        state.session != null &&
                state.lastHttpBlockedAtMillis > 0 &&
                getTimeMillis() - state.lastHttpBlockedAtMillis < stickyWindow.inWholeMilliseconds
    }

    private suspend fun <T> loadInBrowser(
        host: String,
        url: String,
        expectation: PageExpectation<T>,
    ): PageVerdict<T>? {
        val session = lock.withLock {
            hostStates[host]?.session?.also {
                it.refCount++
                it.lastUsedAtMillis = getTimeMillis()
            }
        } ?: return null

        try {
            val browser = session.browser
            browser.navigate(url)
            var last: PageVerdict<T>? = null
            val decisive = withTimeoutOrNull(browserLoadTimeout) {
                browserPages(browser, host).first { page ->
                    val v = evaluator.evaluate(page, expectation)
                    last = v

                    v is PageVerdict.Ok || (v is PageVerdict.Blocked && v.reason !is BlockReason.Captcha)
                }
            }
            val verdict = last ?: return null
            if (verdict is PageVerdict.Ok) {
                decisive?.let { syncBrowserIdentity(host, browser, it.finalUrl, url) }
            }
            return verdict
        } finally {
            lock.withLock {
                session.refCount--
                session.lastUsedAtMillis = getTimeMillis()
            }
        }
    }

    private fun browserPages(browser: CaptchaBrowser, host: String) = merge(
        browser.pageLoads,
        flow {
            while (true) {
                delay(SNAPSHOT_POLL_INTERVAL)
                browser.currentPage()?.let { emit(it) }
            }
        },
    ).let { upstream ->
        flow {
            upstream.collect { page ->
                if (isRelevantPage(page, host)) emit(page)
            }
        }
    }

    private fun isRelevantPage(page: LoadedPage, host: String): Boolean {
        if (page.finalUrl.isBlank() ||
            page.finalUrl == "about:blank" ||
            page.finalUrl.startsWith("chrome-error://")
        ) {
            return false
        }
        val pageHost = normalizedSessionHost(page.finalUrl) ?: return false
        return pageHost == host || pageHost.endsWith(".$host") || host.endsWith(".$pageHost")
    }

    private suspend fun httpFetch(url: String): LoadedPage = withContext(ioContext) {
        try {
            client.use {
                prepareGet(url) {
                    accept(ContentType.Text.Html)
                }.execute { response ->
                    response.toLoadedPage()
                }
            }
        } catch (e: ClientRequestException) {

            LoadedPage(
                finalUrl = e.response.request.url.toString(),
                html = runCatching { e.response.bodyAsText() }.getOrDefault(""),
                status = e.response.status.value,
                retryAfter = parseRetryAfter(e.response.headers[HttpHeaders.RetryAfter]),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private suspend fun HttpResponse.toLoadedPage(): LoadedPage {
        var html = bodyAsText()
        if (html.startsWith("\"")) {

            html = runCatching {
                Json.parseToJsonElement(html).jsonPrimitive.content
            }.getOrNull() ?: html
        }
        return LoadedPage(
            finalUrl = request.url.toString(),
            html = html,
            status = status.value,
            retryAfter = parseRetryAfter(headers[HttpHeaders.RetryAfter]),
        )
    }

    private fun parseRetryAfter(header: String?): Duration? {
        return header?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.seconds
    }

    suspend fun solve(request: SolveRequest, interactive: Boolean): SolveOutcome {
        val host = normalizedSessionHost(request.pageUrl)
            ?: return SolveOutcome.Failed(null)
        if (interactive && !isInteractiveSupported) {
            return SolveOutcome.Unsupported
        }

        val (active, isNew) = lock.withLock {
            val state = hostStateLocked(host)
            val existing = state.activeSolve
            if (existing != null) {
                existing to false
            } else {
                ActiveSolve(interactive).also { state.activeSolve = it } to true
            }
        }
        if (!isNew) {
            return active.deferred.await()
        }

        val job = backgroundScope.launch {
            val outcome = try {
                runSolve(host, request, active.interactive)
            } catch (_: CancellationException) {
                SolveOutcome.Cancelled
            } catch (e: Throwable) {
                logger.error(e) { "WebSessionManager: solve failed for $host" }
                SolveOutcome.Failed(null)
            }
            val now = getTimeMillis()
            lock.withLock {
                val state = hostStateLocked(host)
                when (outcome) {
                    SolveOutcome.Solved -> {
                        state.lastSolveSucceededAtMillis = now
                        state.lastSolveFailedAtMillis = 0
                    }

                    is SolveOutcome.Failed -> state.lastSolveFailedAtMillis = now
                    else -> {}
                }
                if (state.activeSolve === active) {
                    state.activeSolve = null
                }
            }
            active.deferred.complete(outcome)
        }
        active.job = job
        return active.deferred.await()
    }

    private suspend fun runSolve(host: String, request: SolveRequest, interactive: Boolean): SolveOutcome {
        if (!interactive) {
            return runAutoSolve(host, request)
        }
        return runInteractiveSolve(host, request)
    }

    private suspend fun runAutoSolve(host: String, request: SolveRequest): SolveOutcome {
        if (!solverEnabled()) {
            return SolveOutcome.Failed(BlockReason.Captcha(request.kind))
        }
        val applicable = solvers.filter { solver ->
            (request.kind.let { BlockReason.Captcha(it) }).let { solver.canAttempt(it, host) }
        }
        if (applicable.isEmpty()) {

            return SolveOutcome.Failed(BlockReason.Captcha(request.kind))
        }

        val inCooldown = lock.withLock {
            val state = hostStateLocked(host)
            state.lastSolveFailedAtMillis > 0 &&
                    getTimeMillis() - state.lastSolveFailedAtMillis < solveFailCooldown.inWholeMilliseconds
        }
        if (inCooldown) {
            return SolveOutcome.Failed(BlockReason.Captcha(request.kind))
        }

        var lastFailure: SolveOutcome = SolveOutcome.Failed(BlockReason.Captcha(request.kind))
        for (solver in applicable) {
            var acquired: BrowserSession? = null
            var solvedPage: LoadedPage? = null
            val ctx = SolveContext(
                request = request,
                http = client,
                acquireBrowser = {
                    (acquired ?: acquireSession(host).also { acquired = it }).browser
                },
                evaluate = { page -> evaluator.evaluate(page, request.expectation) },
                retainSolvedPage = { page -> solvedPage = page },
            )
            try {
                when (val outcome = solver.attempt(ctx)) {
                    SolveOutcome.Solved -> {
                        solvedPage?.let { retainPendingSolvedPage(host, request.pageUrl, it) }
                        acquired?.let { syncBrowserIdentity(host, it.browser, request.pageUrl, request.pageUrl) }
                        return SolveOutcome.Solved
                    }

                    else -> lastFailure = outcome
                }
            } finally {
                acquired?.let { releaseSession(it) }
            }
        }
        return lastFailure
    }

    private suspend fun runInteractiveSolve(host: String, request: SolveRequest): SolveOutcome {
        val session = acquireSession(host)
        try {
            val browser = session.browser
            val userDecision = CompletableDeferred<SolveOutcome>()
            val ui = InteractiveSolveUi(
                request = request,
                browser = browser,
                onConfirm = {
                    backgroundScope.launch {

                        val verdict = browser.currentPage()
                            ?.takeIf { isRelevantPage(it, host) }
                            ?.let { evaluator.evaluate(it, request.expectation) }
                        userDecision.complete(
                            when (verdict) {
                                is PageVerdict.Ok -> SolveOutcome.Solved
                                is PageVerdict.Blocked -> SolveOutcome.Failed(verdict.reason)
                                else -> SolveOutcome.Failed(null)
                            },
                        )
                    }
                },
                onDismiss = {
                    userDecision.complete(SolveOutcome.Cancelled)
                },
                onRefresh = {
                    backgroundScope.launch {
                        val current = browser.currentPage()?.finalUrl
                            ?.takeIf { it.isNotBlank() && it != "about:blank" && !it.startsWith("chrome-error://") }
                        browser.navigate(current ?: request.pageUrl)
                    }
                },
            )
            publishUi(ui)
            try {
                return coroutineScope {
                    val watcher = launch {
                        browserPages(browser, host).collect { page ->
                            if (evaluator.evaluate(page, request.expectation) is PageVerdict.Ok) {
                                userDecision.complete(SolveOutcome.Solved)
                            }
                        }
                    }
                    browser.navigate(request.pageUrl)
                    val outcome = userDecision.await()
                    watcher.cancel()
                    if (outcome == SolveOutcome.Solved) {
                        val finalUrl = browser.currentPage()?.finalUrl ?: request.pageUrl
                        syncBrowserIdentity(host, browser, finalUrl, request.pageUrl)
                    }
                    outcome
                }
            } finally {
                removeUi(ui)
            }
        } finally {
            releaseSession(session)
        }
    }

    fun cancelAutoSolves() {
        backgroundScope.launch {
            val jobs = lock.withLock {
                hostStates.values.mapNotNull { state ->
                    state.activeSolve?.takeIf { !it.interactive }?.job
                }
            }
            jobs.forEach { it.cancel() }
        }
    }

    suspend fun invalidate(host: String) {
        val normalized = normalizedSessionHost("https://$host") ?: return
        val session = lock.withLock {
            hostStates[normalized]?.let { state ->
                state.lastSolveSucceededAtMillis = 0
                state.pendingSolvedPage = null
                state.session?.also { state.session = null }
            }
        }
        cookieJar.clearForHost(normalized)
        session?.let { closeSession(it) }
    }

    private suspend fun retainPendingSolvedPage(host: String, requestUrl: String, page: LoadedPage) {
        lock.withLock {
            hostStateLocked(host).pendingSolvedPage = PendingSolvedPage(
                requestKey = pendingPageKey(requestUrl),
                page = page,
                storedAtMillis = getTimeMillis(),
            )
        }
    }

    private suspend fun consumePendingSolvedPage(host: String, requestUrl: String): LoadedPage? = lock.withLock {
        val state = hostStates[host] ?: return@withLock null
        val pending = state.pendingSolvedPage ?: return@withLock null
        if (getTimeMillis() - pending.storedAtMillis >= stickyWindow.inWholeMilliseconds) {
            state.pendingSolvedPage = null
            return@withLock null
        }
        if (pending.requestKey != pendingPageKey(requestUrl)) return@withLock null
        state.pendingSolvedPage = null
        pending.page
    }

    private fun pendingPageKey(url: String): String = runCatching { Url(url).toString() }.getOrDefault(url)

    private suspend fun syncBrowserIdentity(
        host: String,
        browser: CaptchaBrowser,
        finalUrl: String,
        requestedUrl: String,
    ) {
        val urls = listOfNotNull(
            finalUrl,
            requestedUrl,
            normalizedStorageOrigin(finalUrl),
            normalizedStorageOrigin(requestedUrl),
        ).distinct()
        val cookies = runCatching { browser.collectCookies(urls) }
            .onFailure { logger.error(it) { "WebSessionManager: failed to collect cookies for $host" } }
            .getOrDefault(emptyList())
        val cookieTargets = urls.filter(String::isNotBlank)
        if (cookies.isNotEmpty()) {
            cookieTargets.forEach { target ->
                cookieJar.addBrowserCookies(target, cookies)
            }
        }
        val userAgent = withContext(Dispatchers.Main) { browser.userAgent }
        listOfNotNull(
            host,
            normalizedSessionHost(requestedUrl),
            normalizedSessionHost(finalUrl),
        ).distinct().forEach { targetHost ->
            identityRegistry.setUserAgent(targetHost, userAgent)
        }
        logger.info { "WebSessionManager: synced ${cookies.size} cookies and UA for ${cookieTargets.joinToString()}" }
    }

    suspend fun extractVideoResource(
        pageUrl: String,
        timeoutMillis: Long,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ): WebResource? {
        val host = normalizedSessionHost(pageUrl) ?: return null
        val session = lock.withLock {
            hostStates[host]?.session?.also {
                it.refCount++
                it.lastUsedAtMillis = getTimeMillis()
            }
        } ?: return null

        val browser = session.browser
        val deferred = CompletableDeferred<WebResource>()
        val loadedNestedUrls = mutableSetOf(pageUrl)
        val nestedLock = kotlinx.atomicfu.locks.SynchronizedObject()
        try {
            browser.setResourceInterceptor { url ->
                when (resourceMatcher(url)) {
                    WebViewVideoExtractor.Instruction.Continue -> InterceptDecision.Continue
                    WebViewVideoExtractor.Instruction.FoundResource -> {
                        deferred.complete(WebResource(url))
                        InterceptDecision.Block
                    }

                    WebViewVideoExtractor.Instruction.LoadPage -> {
                        val shouldLoad = kotlinx.atomicfu.locks.synchronized(nestedLock) {
                            loadedNestedUrls.add(url)
                        }
                        if (shouldLoad && deferred.isActive) {
                            backgroundScope.launch { browser.navigate(url) }
                        }
                        InterceptDecision.Continue
                    }
                }
            }
            browser.navigate(pageUrl)
            return withTimeoutOrNull(timeoutMillis.milliseconds) { deferred.await() }
        } finally {
            browser.setResourceInterceptor(null)
            lock.withLock {
                session.refCount--
                session.lastUsedAtMillis = getTimeMillis()
            }
        }
    }

    private suspend fun acquireSession(host: String): BrowserSession {
        lock.withLock {
            hostStates[host]?.session?.let {
                it.refCount++
                it.lastUsedAtMillis = getTimeMillis()
                return it
            }
        }
        return browserCreateSemaphore.withPermit {

            lock.withLock {
                hostStates[host]?.session?.let {
                    it.refCount++
                    it.lastUsedAtMillis = getTimeMillis()
                    return it
                }
            }
            val browser = withContext(ioContext) { browserFactory.create() }
            val session = BrowserSession(browser).apply {
                refCount = 1
                lastUsedAtMillis = getTimeMillis()
            }
            var result: BrowserSession = session
            var duplicate: BrowserSession? = null
            var evicted: List<BrowserSession> = emptyList()
            lock.withLock {
                val state = hostStateLocked(host)
                val existing = state.session
                if (existing != null) {

                    existing.refCount++
                    existing.lastUsedAtMillis = getTimeMillis()
                    result = existing
                    duplicate = session
                } else {
                    state.session = session
                    evicted = evictLruLocked()
                }
            }
            duplicate?.let { closeSession(it) }
            evicted.forEach { closeSession(it) }
            result
        }
    }

    private suspend fun releaseSession(session: BrowserSession) {
        lock.withLock {
            session.refCount--
            session.lastUsedAtMillis = getTimeMillis()
        }
    }

    private fun evictLruLocked(): List<BrowserSession> {
        val withSessions = hostStates.entries.filter { it.value.session != null }
        if (withSessions.size <= maxSessions) return emptyList()
        val excess = withSessions.size - maxSessions
        return withSessions
            .filter { it.value.session!!.refCount <= 0 && it.value.activeSolve == null }
            .sortedBy { it.value.session!!.lastUsedAtMillis }
            .take(excess)
            .map { entry ->
                entry.value.session!!.also { entry.value.session = null }
            }
    }

    private suspend fun sweepIdleSessions() {
        val now = getTimeMillis()
        val toClose = lock.withLock {
            hostStates.entries.mapNotNull { (_, state) ->
                val session = state.session ?: return@mapNotNull null
                if (session.refCount <= 0 &&
                    state.activeSolve == null &&
                    now - session.lastUsedAtMillis > idleTtl.inWholeMilliseconds
                ) {
                    state.session = null
                    session
                } else null
            }
        }
        toClose.forEach { closeSession(it) }
    }

    private suspend fun closeSession(session: BrowserSession) {
        withContext(ioContext) {
            runCatching { session.browser.close() }
                .onFailure { logger.error(it) { "WebSessionManager: failed to close browser" } }
        }
    }

    private companion object {
        private val logger = logger<WebSessionManager>()

        private const val MAX_CONCURRENT_BROWSER_CREATIONS = 2
        private val SESSION_SWEEP_INTERVAL = 30.seconds
        private val SNAPSHOT_POLL_INTERVAL = 2.seconds
    }
}
