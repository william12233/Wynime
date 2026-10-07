package com.wynime.app.platform

import com.jetbrains.cef.JCefAppConfig
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.utils.io.readLastNLines
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.currentTimeMillis
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.callback.CefAuthCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.misc.CefLog
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

object WynimeCefApp {
    private val logger = logger<WynimeCefApp>()

    @Volatile
    private var app: CefApp? = null

    @Volatile
    var currentAppLogFile: File? = null
        private set

    private val lock = Mutex()
    private const val DEFAULT_DATA_SOURCE_BROWSER_LIMIT = 8
    private val dataSourceBrowserGate = DataSourceBrowserGate(DEFAULT_DATA_SOURCE_BROWSER_LIMIT)
    private val disposedApps = Collections.newSetFromMap(IdentityHashMap<CefApp, Boolean>())

    private var proxyServer: Url? = null
    private var proxyAuthUsername: String? = null
    private var proxyAuthPassword: String? = null

    private val proxiedRequestHandler = object : CefRequestHandlerAdapter() {
        override fun getAuthCredentials(
            browser: CefBrowser?,
            originUrl: String?,
            isProxy: Boolean,
            host: String?,
            port: Int,
            realm: String?,
            scheme: String?,
            callback: CefAuthCallback?,
        ): Boolean {
            if (!isProxy) return false
            if (host != proxyServer?.host) return false
            if (port != proxyServer?.port) return false
            if (scheme != proxyServer?.protocol?.name) return false

            if (callback == null) return false
            callback.Continue(proxyAuthUsername, proxyAuthPassword)
            return true
        }
    }

    private fun createCefApp(
        logDir: File,
        cacheDir: File,
        proxyServer: String? = null,
    ): CefApp {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")

        val jcefConfig = JCefAppConfig.getInstance()

        jcefConfig.cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_DEFAULT
        jcefConfig.cefSettings.log_file = logDir
            .resolve("cef-${dateFormat.format(Date(currentTimeMillis()))}.log")
            .also { currentAppLogFile = it }
            .absolutePath
        jcefConfig.cefSettings.windowless_rendering_enabled = true
        jcefConfig.cefSettings.cache_path = cacheDir.absolutePath

        jcefConfig.appArgsAsList.apply {
            add("--mute-audio")
            add("--force-dark-mode")

            proxyServer?.let { add("--proxy-server=${it}") }
        }

        jcefConfig.appArgsAsList.add(
            "--autoplay-policy=no-user-gesture-required",
        )

        CefLog.init(jcefConfig.cefSettings.log_file, jcefConfig.cefSettings.log_severity)
        CefApp.startup(jcefConfig.appArgs)
        return CefApp.getInstance(jcefConfig.appArgs, jcefConfig.cefSettings)
    }

    suspend fun initialize(
        logDir: File,
        cacheDir: File,
        proxyServer: String? = null,
        proxyAuthUsername: String? = null,
        proxyAuthPassword: String? = null,
    ) {
        val currentApp = app
        if (currentApp != null) return

        val cefApp = lock.withLock {
            val currentApp2 = app
            if (currentApp2 != null) return

            val finalCacheDir = if (checkLockFile(cacheDir)) {
                cacheDir
            } else {
                val newTempCacheDir = cacheDir.nameWithoutExtension + "-${currentTimeMillis()}"
                logger.warn { "Failed to resolve JCEF lock file, switch to temporary dir $newTempCacheDir for this instance." }
                cacheDir.parentFile.resolve(newTempCacheDir)
            }

            val newApp = createCefApp(logDir, finalCacheDir, proxyServer)
            this.proxyServer = proxyServer?.let(::Url)
            this.proxyAuthUsername = proxyAuthUsername
            this.proxyAuthPassword = proxyAuthPassword

            Runtime.getRuntime().addShutdownHook(
                thread(start = false) {
                    disposeAppBlocking(newApp)
                },
            )

            app = newApp
            newApp
        }

        withTimeoutOrNull(8.seconds) {
            logger.info { "Awaiting JCEF initialization." }
            suspendCancellableCoroutine<Unit> { cont ->
                cefApp.onInitialization { state ->
                    if (state == CefApp.CefAppState.INITIALIZED && cont.isActive) {
                        logger.info { "JCEF is initialized." }
                        cont.resume(Unit)
                    }
                }
            }
        }.also { result ->
            if (result == null) {

                app = null
                disposeAppBlocking(cefApp)
                throw JCEFInitializationException(
                    "Failed to initialize JCEF, state: ${CefApp.getState()}, " +
                            "last cef logs: \n${getLatestCefLog().joinToString("\n")}",
                )
            }
        }
    }

    private fun checkLockFile(cacheDir: File): Boolean {
        if (!cacheDir.exists()) return true

                val lockFile = cacheDir.resolve("lockfile")
                if (!lockFile.exists() || !lockFile.isFile) return true

                return try {
                    Files.delete(lockFile.toPath())
                    true
                } catch (e: IOException) {

                    logger.error(e) { "Lock file exists and cannot be deleted while initializing JCEF." }
                    false
                }

    }

    fun createClient(): CefClient? {
        return app?.createClient()
            ?.apply { addRequestHandler(proxiedRequestHandler) }
    }

    fun configureDataSourceBrowserLimit(limit: Int) {
        dataSourceBrowserGate.configureLimit(limit)
    }

    suspend fun acquireDataSourceBrowserPermit(): BrowserLifecyclePermit {
        return dataSourceBrowserGate.acquire()
    }

    class BrowserLifecyclePermit internal constructor(
        private val releasePermit: () -> Unit,
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                releasePermit()
            }
        }
    }

    suspend fun closeBrowserAndDisposeClient(
        browser: CefBrowser?,
        client: CefClient?,
    ) {
        suspendCoroutineOnCefContext {
            closeBrowserAndDisposeClientNow(browser, client)
        }
    }

    fun closeBrowserAndDisposeClientBlocking(
        browser: CefBrowser?,
        client: CefClient?,
    ) {
        blockOnCefContext {
            closeBrowserAndDisposeClientNow(browser, client)
        }
    }

    fun disposeBlocking() {
        val currentApp = app ?: return
        app = null
        disposeAppBlocking(currentApp)
    }

    private fun disposeAppBlocking(target: CefApp) {
        val shouldDispose = synchronized(disposedApps) {
            disposedApps.add(target)
        }
        if (!shouldDispose) return

        runCatching {
            if (SwingUtilities.isEventDispatchThread()) {
                target.dispose()
            } else {

                val latch = CountDownLatch(1)
                SwingUtilities.invokeLater {
                    try {
                        target.dispose()
                    } finally {
                        latch.countDown()
                    }
                }
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    logger.warn { "Timed out waiting for JCEF disposal on EDT, proceeding without it." }
                }
            }
        }.onFailure {
            logger.warn(it) { "Failed to dispose JCEF." }
        }
    }

    private fun closeBrowserAndDisposeClientNow(
        browser: CefBrowser?,
        client: CefClient?,
    ) {
        runCatching {
            browser?.close(true)
        }.onFailure {
            logger.warn(it) { "Failed to close CEF browser." }
        }
        runCatching {
            client?.dispose()
        }.onFailure {
            logger.warn(it) { "Failed to dispose CEF client." }
        }
    }

    fun runOnCefContext(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }

    fun blockOnCefContext(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeAndWait(block)
        }
    }

    suspend fun <T> suspendCoroutineOnCefContext(block: () -> T): T {
        return suspendCancellableCoroutine {
            runOnCefContext {
                it.resumeWith(runCatching(block))
            }
        }
    }

    fun getLatestCefLog(nLine: Int = 30): List<String> {
        val file = currentAppLogFile ?: return emptyList()
        return file.readLastNLines(nLine)
    }
}

private class JCEFInitializationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

private class DataSourceBrowserGate(initialLimit: Int) {
    private val lock = ReentrantLock()
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()
    private var limit = initialLimit.coerceAtLeast(1)
    private var acquired = 0

    suspend fun acquire(): WynimeCefApp.BrowserLifecyclePermit {
        while (true) {
            val waiter = lockAndGetWaiter()
            if (waiter == null) {
                return WynimeCefApp.BrowserLifecyclePermit(::release)
            }

            try {
                waiter.await()
                return WynimeCefApp.BrowserLifecyclePermit(::release)
            } catch (e: Throwable) {
                val wakeups = cancelWaiter(waiter)
                wakeups.forEach { it.complete(Unit) }
                throw e
            }
        }
    }

    fun configureLimit(newLimit: Int) {
        val wakeups = lockAndConfigureLimit(newLimit.coerceAtLeast(1))
        wakeups.forEach { it.complete(Unit) }
    }

    private fun release() {
        val wakeups = lockAndRelease()
        wakeups.forEach { it.complete(Unit) }
    }

    private fun lockAndGetWaiter(): CompletableDeferred<Unit>? {
        lock.lock()
        try {
            if (acquired < limit) {
                acquired++
                return null
            }
            return CompletableDeferred<Unit>().also(waiters::addLast)
        } finally {
            lock.unlock()
        }
    }

    private fun cancelWaiter(waiter: CompletableDeferred<Unit>): List<CompletableDeferred<Unit>> {
        lock.lock()
        try {
            if (waiters.remove(waiter)) {
                return emptyList()
            }
            acquired--
            return collectWakeupsLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun lockAndConfigureLimit(newLimit: Int): List<CompletableDeferred<Unit>> {
        lock.lock()
        try {
            limit = newLimit
            return collectWakeupsLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun lockAndRelease(): List<CompletableDeferred<Unit>> {
        lock.lock()
        try {
            check(acquired > 0) { "Browser lifecycle permit released without being acquired" }
            acquired--
            return collectWakeupsLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun collectWakeupsLocked(): List<CompletableDeferred<Unit>> {
        val wakeups = mutableListOf<CompletableDeferred<Unit>>()
        while (acquired < limit && waiters.isNotEmpty()) {
            acquired++
            wakeups += waiters.removeFirst()
        }
        return wakeups
    }
}
