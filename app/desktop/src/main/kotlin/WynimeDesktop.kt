package com.wynime.app.desktop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.data.models.preference.DebugSettings
import com.wynime.app.data.models.preference.UISettings
import com.wynime.app.data.repository.SavedWindowState
import com.wynime.app.data.repository.WindowStateRepository
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.desktop.storage.AppFolderResolver
import com.wynime.app.desktop.storage.AppInfo
import com.wynime.app.desktop.window.WindowFrame
import com.wynime.app.domain.settings.ProxyProvider
import com.wynime.app.domain.update.UpdateManager
import com.wynime.app.navigation.WynimeNavigator
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.platform.WynimeBuildConfigDesktop
import com.wynime.app.platform.WynimeCefApp
import com.wynime.app.platform.AppStartupTasks
import com.wynime.app.platform.DesktopContext
import com.wynime.app.platform.ExtraWindowProperties
import com.wynime.app.platform.JvmLogHelper
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.PlatformWindow
import com.wynime.app.platform.StartupTimeMonitor
import com.wynime.app.platform.StepName
import com.wynime.app.platform.create
import com.wynime.app.platform.createAppRootCoroutineScope
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.platform.getCommonKoinModule
import com.wynime.app.platform.startCommonKoinModule
import com.wynime.app.platform.trace.recordAppStart
import com.wynime.app.platform.window.HandleWindowsWindowProc
import com.wynime.app.platform.window.LocalTitleBarThemeController
import com.wynime.app.platform.window.WindowsWindowUtils
import com.wynime.app.platform.window.rememberLayoutHitTestOwner
import com.wynime.app.platform.window.setTitleBar
import com.wynime.app.tools.update.DesktopUpdateInstaller
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.LocalWindowState
import com.wynime.app.ui.foundation.WindowDropHost
import com.wynime.app.ui.foundation.effects.OverrideCaptionButtonAppearance
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.app.ui.foundation.layout.LocalSecondaryWindowFrame
import com.wynime.app.ui.foundation.layout.isSystemInFullscreen
import com.wynime.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import com.wynime.app.ui.foundation.navigation.OnBackPressedDispatcher
import com.wynime.app.ui.foundation.navigation.SkikoOnBackPressedDispatcherOwner
import com.wynime.app.ui.foundation.navigation.BackKeyEventHandler
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.LocalSystemDarkThemeOverride
import com.wynime.app.ui.foundation.theme.LocalThemeSettings
import com.wynime.app.ui.foundation.theme.isSystemInDarkThemeDetected
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.Toast
import com.wynime.app.ui.foundation.widgets.ToastViewModel
import com.wynime.app.ui.foundation.widgets.Toaster
import com.wynime.app.ui.main.WynimeApp
import com.wynime.app.ui.main.WynimeAppContent
import com.wynime.app.ui.update.InstallPackageDropDialogs
import com.wynime.app.ui.update.rememberDropInstallPackageState
import com.wynime.app.ui.update.rememberInstallPackageDropHandler
import com.wynime.desktop.generated.resources.Res
import com.wynime.desktop.generated.resources.a_round
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsConfig
import com.wynime.utils.analytics.AnalyticsImpl
import com.wynime.utils.analytics.AnalyticsSecrets
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.trace
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.currentPlatform
import com.wynime.utils.platform.currentPlatformDesktop
import com.wynime.utils.platform.isWindows
import com.wynime.utils.video.enhancement.shader.provider.VideoEnhancementShaderProvider
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import org.openani.mediamp.ffmpeg.FFmpegKit
import org.openani.mediamp.mpv.MPVHandle
import java.awt.Desktop
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess

private val logger by lazy { logger("Wynime") }
private inline val toplevelLogger get() = logger

object WynimeDesktop {

    init {
        System.setProperty("native.encoding", "UTF-8")
    }

    private fun prepareMpvLibraries(composeResDir: String) {
        try {
            val devNativeDir = System.getProperty("mediamp.mpv.dev.native.dir")
            if (devNativeDir != null) {

                MPVHandle.setRuntimeLibraryDirectory(devNativeDir, false)
            } else if (currentProcessName()?.contains("java") == true) {
                MPVHandle.useDefaultRuntimeLibraryDirectory()
            } else {
                MPVHandle.setRuntimeLibraryDirectory(composeResDir, false)
            }
            val mpvLogger = logger<MPVHandle>()

            MPVHandle.setLogHandler {
                val prefix = it.prefix.padStart(9, ' ')
                val handle = "0x${it.instanceHandle.toHexString().trimStart('0')}"
                if (it.level in 1..20) {
                    mpvLogger.error { "[$prefix@$handle] ${it.line}" }
                } else if (it.level <= 30) {
                    mpvLogger.warn { "[$prefix@$handle] ${it.line}" }
                } else if (it.level <= 40) {
                    mpvLogger.info { "[$prefix@$handle] ${it.line}" }
                } else if (it.level <= 50) {
                    mpvLogger.debug { "[$prefix@$handle] ${it.line}" }
                } else {
                    mpvLogger.trace { "[$prefix@$handle] ${it.line}" }
                }
            }
            logger.info { "mediampv is loaded." }
        } catch (e: Throwable) {
            logger.error(e) { "Failed to load libmpv component of mediamp." }
        }
    }

    private fun calculateWindowSize(
        desiredWidth: Dp,
        desiredHeight: Dp,
        screenSize: DpSize = ScreenUtils.getScreenSize()
    ): DpSize {
        return DpSize(
            width = if (desiredWidth > screenSize.width) screenSize.width else desiredWidth,
            height = if (desiredHeight > screenSize.height) screenSize.height else desiredHeight,
        )
    }

    private fun isRunningUnderWine(): Boolean {
        return if (currentPlatform().isWindows()) {
            Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, "Software\\Wine")
        } else {
            false
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val startupTimeMonitor = StartupTimeMonitor()

        val originalExceptionHandler = Thread.currentThread().uncaughtExceptionHandler
        Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
            logger.error(e) { "!!!WYNIME FATAL EXCEPTION!!!" }
            e.printStackTrace()
            Thread.sleep(1000)
            originalExceptionHandler.uncaughtException(t, e)
            Thread.sleep(5000)
            exitProcess(1)
        }
        startupTimeMonitor.mark(StepName.UncaughtExceptionHandler)

        val projectDirectories = AppFolderResolver.INSTANCE.resolve(
            AppInfo(
                "me",
                "Him188",
                if (WynimeBuildConfigDesktop.isDebug) "Wynime-debug" else "Wynime",
            ),
        )
        val dataDir = projectDirectories.data
        val cacheDir = projectDirectories.cache
        startupTimeMonitor.mark(StepName.ProjectDirectories)

        val logsDir = dataDir.resolve("logs").toFile().apply { mkdirs() }

        Log4j2Config.configureLogging(logsDir)

        if (WynimeBuildConfigDesktop.isDebug) {
            logger.info { "Debug mode enabled" }
        }
        AppStartupTasks.printVersions()
        startupTimeMonitor.mark(StepName.Logging)

        logger.info { "dataDir: file://${dataDir.absolutePathString().replace(" ", "%20")}" }
        logger.info { "cacheDir: file://${cacheDir.absolutePathString().replace(" ", "%20")}" }
        logger.info { "logsDir: file://${logsDir.absolutePath.replace(" ", "%20")}" }
        val coroutineScope = createAppRootCoroutineScope()

        coroutineScope.launch(Dispatchers.IO) {
            kotlin.runCatching {
                JvmLogHelper.deleteOldLogs(logsDir.toPath())
            }.onFailure {
                logger.error(it) { "Failed to delete old logs" }
            }
        }

        val defaultSize = DpSize(1301.dp, 855.dp)

        val windowState = WindowState(
            size = kotlin.runCatching {
                calculateWindowSize(defaultSize.width, defaultSize.height)
            }.onFailure {
                logger.error(it) { "Failed to calculate window size" }
            }.getOrElse {
                defaultSize
            },
            position = WindowPosition.Aligned(Alignment.Center),
        )
        val context = DesktopContext(
            windowState,
            dataDir.toFile(),
            cacheDir.toFile(),
            logsDir,
            ExtraWindowProperties(),
        )
        startupTimeMonitor.mark(StepName.WindowAndContext)

        SingleInstanceChecker.instance.ensureSingleInstance()
        startupTimeMonitor.mark(StepName.SingletonChecker)

        val koin = startKoin {
            modules(getCommonKoinModule({ context }, coroutineScope))
            modules(getDesktopModules({ context }, coroutineScope))
        }.startCommonKoinModule(context, coroutineScope)
        startupTimeMonitor.mark(StepName.Modules)

        System.getenv("WYNIME_DESKTOP_TEST_TASK")?.let { taskName ->
            logger.info { "Running test task: $taskName" }

            val argc = System.getenv("WYNIME_DESKTOP_TEST_ARGC")?.toIntOrNull() ?: 0
            val args = (0 until argc).mapNotNull { i ->
                System.getenv("WYNIME_DESKTOP_TEST_ARGV_$i")
            }
            TestTasks.handleTestTask(taskName, args, context)
        }
        val settingsRepository = koin.koin.get<SettingsRepository>()
        val userRepository = koin.koin.get<UserRepository>()

        coroutineScope.launch {
            settingsRepository.videoResolverSettings.flow
                .map { it.effectiveDataSourceBrowserConcurrency }
                .distinctUntilChanged()
                .collect { WynimeCefApp.configureDataSourceBrowserLimit(it) }
        }

        val setLocaleJob = coroutineScope.launch {
            settingsRepository.uiSettings.flow.first().appLanguage?.platformLocale?.let {
                logger.info { "Set locale to $it" }
                Locale.setDefault(it)
            }
        }

        val analyticsInitializer = coroutineScope.launch {
            val settings = settingsRepository.analyticsSettings.flow.first()
            settingsRepository.analyticsSettings.update { settings }
            if (settings.isBugReportEnabled) {
                AppStartupTasks.initializeSentry(settings.deviceId)
            }
            if (settings.isAnalyticsEnabled) {
                AppStartupTasks.initializeAnalytics {
                    AnalyticsImpl(
                        AnalyticsConfig.create(),
                        settings.deviceId,
                        userId = { userRepository.selfInfoFlow.first()?.id },
                        AnalyticsSecrets(
                            apiSecret = WynimeBuildConfigDesktop.firebaseGAApiSecret,
                            firebaseAppId = WynimeBuildConfigDesktop.firebaseGAAppId,
                        ),
                        coroutineScope.coroutineContext,
                    ).apply {

                        init()
                    }
                }
            }
        }

        val composeResDir = File(System.getProperty("compose.application.resources.dir"))
            .parentFile.absolutePath

        val loadLibraryJob = coroutineScope.launch(Dispatchers.IO) {
            configureLibrariesAndResources(composeResDir)
        }

        coroutineScope.launch {
            logger.info { "[JCEF init] awaiting native media libraries." }
            try {
                analyticsInitializer.join()
                loadLibraryJob.join()
            } catch (_: Throwable) {
            }
            val proxySettings = koin.koin.get<ProxyProvider>()
                .proxy.first()

            initializeJcefAndPlayerBackend(
                preparePlayerBeforeJcef = shouldPreparePlayerBeforeJcef(currentPlatformDesktop()),
                preparePlayer = {
                    withContext(Dispatchers.IO) {
                        prepareMpvLibraries(composeResDir)
                    }
                },
                initializeJcef = {
                    logger.info { "[JCEF init] initializing WynimeCefApp." }
                    WynimeCefApp.initialize(
                        logDir = dataDir.toFile().resolve("logs"),
                        cacheDir = cacheDir.toFile().resolve("jcef-cache"),
                        proxyServer = proxySettings?.url,
                        proxyAuthUsername = proxySettings?.authorization?.username,
                        proxyAuthPassword = proxySettings?.authorization?.password,
                    )
                    logger.info { "[JCEF init] WynimeCefApp is initialized." }
                },
            )
        }

        coroutineScope.launch {
            kotlin.runCatching {
                val desktopUpdateInstaller = koin.koin.get<UpdateInstaller>() as DesktopUpdateInstaller
                desktopUpdateInstaller.deleteOldUpdater()
            }.onFailure {
                logger.error(it) { "Failed to delete update installer" }
            }

            kotlin.runCatching {
                koin.koin.get<UpdateManager>().deleteInstalledFiles()
            }.onFailure {
                logger.error(it) { "Failed to delete installed files" }
            }
        }
        startupTimeMonitor.mark(StepName.LaunchAsyncInitializers)

        if (currentWynimeBuildConfig.isDebug) {
            runCatching {
                PagingLoggingHack.install()
                logger.trace { "Successfully instrumented PagingLogging" }
            }.onFailure {
                logger.error(it) { "Failed to install paging logging hack" }
            }
            startupTimeMonitor.mark(StepName.PagingHack)
        }

        val navigator = WynimeNavigator()
        DesktopMediaTraceCapture.install(koin.koin, coroutineScope)?.start(navigator)

        val windowStateRepository = koin.koin.get<WindowStateRepository>()
        val savedWindowStateDeferred = coroutineScope.async {
            windowStateRepository.flow.firstOrNull()
        }

        val jobsToWait = listOf(
            setLocaleJob,
            analyticsInitializer,
            savedWindowStateDeferred,
        )
        if (jobsToWait.any { it.isActive }) {
            runBlocking { jobsToWait.joinAll() }
        }
        startupTimeMonitor.mark(StepName.Analytics)

        val systemThemeDetector = SystemThemeDetector()
        startupTimeMonitor.mark(StepName.ThemeDetector)

        coroutineScope.launch {
            Analytics.recordAppStart(startupTimeMonitor)
        }
        logger.info {
            "App startup breakdown: \n" +
                    startupTimeMonitor.getMarks().entries.joinToString("\n") { " - ${it.key}: ${it.value}ms" } +
                    "\nTotal time: ${startupTimeMonitor.getTotalDuration().inWholeMilliseconds}ms"
        }
        val savedWindowState: SavedWindowState? = savedWindowStateDeferred.getCompleted()
        restoreWindowState(windowState, savedWindowState)

        application {
            val saveCurrentWindowState = remember(windowState, windowStateRepository) {
                {
                    saveWindowState(windowState) {
                        runBlocking {
                            windowStateRepository.update(it)
                        }
                    }
                }
            }
            val exitApplicationSavingWindowState = remember(saveCurrentWindowState) {
                {
                    saveCurrentWindowState()
                    WynimeCefApp.disposeBlocking()
                    exitApplication()
                }
            }

            DisposableEffect(saveCurrentWindowState) {
                onDispose {
                    saveCurrentWindowState()
                }
            }

            val uiSettings by settingsRepository.uiSettings.flow.collectAsState(UISettings.Default)

            val alwaysOnTopState = remember { mutableStateOf(false) }
            val trayState = rememberWynimeTrayState()
            val appIcon = painterResource(Res.drawable.a_round)

            WynimeSystemTray(
                state = trayState,
                icon = appIcon,
                tooltip = "Wynime",
                onExit = exitApplicationSavingWindowState,
            )

            val backPressedDispatcher = remember(navigator) {
                OnBackPressedDispatcher(fallback = { navigator.popBackStack() })
            }
            val backKeyEventHandler = remember { BackKeyEventHandler() }
            Window(
                visible = !trayState.isWindowHiddenToTray,
                onCloseRequest = {
                    trayState.handleCloseRequest(
                        closeBehavior = uiSettings.desktopCloseBehavior,
                        onExit = exitApplicationSavingWindowState,
                    )
                },
                state = windowState,
            title = "Wynime",
                icon = appIcon,
                alwaysOnTop = alwaysOnTopState.value,

                onKeyEvent = { event -> backKeyEventHandler.onKeyEvent(event, backPressedDispatcher::onBackPressed) },
            ) {

                val lifecycleOwner = LocalLifecycleOwner.current
                val backPressedDispatcherOwner = remember(backPressedDispatcher, lifecycleOwner) {
                    SkikoOnBackPressedDispatcherOwner(backPressedDispatcher, lifecycleOwner)
                }

                DisposableEffect(Unit) {
                    window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                    window.toFront()
                    window.requestFocus()
                    onDispose {}
                }

                SideEffect {

                    window.background = java.awt.Color.BLACK
                    window.contentPane.background = java.awt.Color.BLACK
                    window.minimumSize = java.awt.Dimension(400, 400)

                    logger.info {
                        "renderApi: " + this.window.renderApi
                    }
                }

                val systemIsDark by systemThemeDetector.isDark.collectAsStateWithLifecycle()
                val platform = LocalPlatform.current

                val layoutHitTestOwner = if (platform.isWindows()) {
                    rememberLayoutHitTestOwner()
                } else {
                    null
                }
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalWindowState provides windowState,
                    LocalPlatformWindow provides remember(window.windowHandle, this, platform, windowState) {
                        PlatformWindow(
                            windowHandle = window.windowHandle,
                            windowScope = this,
                            platform = platform,
                            windowState = windowState,
                            layoutHitTestOwner = layoutHitTestOwner,
                            alwaysOnTopState = alwaysOnTopState,
                        )
                    },
                    LocalOnBackPressedDispatcherOwner provides backPressedDispatcherOwner,
                    LocalSystemDarkThemeOverride provides systemIsDark,

                    LocalSecondaryWindowFrame provides if (isRunningUnderWine()) {
                        null
                    } else {
                        { secondaryWindowState, onCloseRequest, content ->
                            HandleWindowsWindowProc()
                            WindowFrame(secondaryWindowState, onCloseRequest, content)
                        }
                    },
                ) {
                    if (isRunningUnderWine()) {
                        MainWindowContent(navigator, settingsRepository)
                    } else {
                        HandleWindowsWindowProc()
                        if (platform.isWindows()) {
                            val platformWindow = LocalPlatformWindow.current
                            LaunchedEffect(platformWindow, trayState) {
                                WindowsWindowUtils.instance.windowIsActive(platformWindow)
                                    .filter { it == true }
                                    .collect {
                                        if (trayState.isWindowHiddenToTray) {
                                            trayState.restoreWindow()
                                        }
                                    }
                            }
                        }
                        WindowFrame(
                            windowState = windowState,
                            onCloseRequest = {
                                trayState.handleCloseRequest(
                                    closeBehavior = uiSettings.desktopCloseBehavior,
                                    onExit = exitApplicationSavingWindowState,
                                )
                            },
                        ) {
                            MainWindowContent(navigator, settingsRepository)
                        }
                    }
                }
            }

        }

    }

    private fun configureLibrariesAndResources(composeResDir: String) {
        try {
            if (currentProcessName()?.contains("java") == true) {
                FFmpegKit.useDefaultRuntimeLibraryDirectory()
            } else {
                FFmpegKit.setRuntimeLibraryDirectory(composeResDir, false)
            }
            logger.info { "FFmpegKit is loaded." }
        } catch (e: Throwable) {
            logger.error(e) { "Failed to load FFmpeg component of mediamp." }
        }

        if (currentProcessName()?.contains("java") != true) {
            val shaderPath = Path.of(composeResDir, "resources", "anime4k")
            VideoEnhancementShaderProvider.setShaderBasePath(Path.of(composeResDir, "resources", "anime4k"))
            logger.info { "Using bundled video enhancement shaders from $${shaderPath.toAbsolutePath()}" }
        }
    }

    fun currentProcessName(): String? {
        return ProcessHandle.current()
            .info()
            .command()
            .map { Paths.get(it).fileName.toString() }
            .orElse(null)
    }
}

@OptIn(InternalComposeUiApi::class)
@Composable
private fun FrameWindowScope.MainWindowContent(
    wynimeNavigator: WynimeNavigator,
    settingsRepository: SettingsRepository,
) {
    WynimeApp {
        val themeSettings = LocalThemeSettings.current
        val titleBarThemeController = LocalTitleBarThemeController.current
        val systemIsDark = isSystemInDarkThemeDetected()
        val navContainerColor = WynimeThemeDefaults.navigationContainerColor

        val isTitleBarDark = remember(themeSettings, systemIsDark) {
            when (themeSettings.darkMode) {
                DarkMode.AUTO -> systemIsDark
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
        }
        DisposableEffect(isTitleBarDark, navContainerColor, titleBarThemeController) {
            window.setTitleBar(navContainerColor, isTitleBarDark)
            onDispose {}
        }

        OverrideCaptionButtonAppearance(isDark = isTitleBarDark)

        Box(
            Modifier
                .ifThen(!isSystemInFullscreen()) {
                    statusBarsPadding()
                }
                .fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                val paddingByWindowSize by animateDpAsState(0.dp)

                val vm = viewModel { ToastViewModel() }

                val showing by vm.showing.collectAsStateWithLifecycle()
                val content by vm.content.collectAsStateWithLifecycle()

                CompositionLocalProvider(
                    LocalNavigator provides wynimeNavigator,
                    LocalToaster provides remember(vm) {
                        object : Toaster {
                            override fun toast(text: String) {
                                vm.show(text)
                            }
                        }
                    },
                    LocalContextMenuRepresentation provides DesktopContextMenuRepresentation,
                ) {
                    Box(Modifier.padding(all = paddingByWindowSize)) {

                        val installPackageOnDrop by remember(settingsRepository) {
                            settingsRepository.debugSettings.flow
                                .map { it.enabled && it.installPackageOnDrop }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(DebugSettings.Default.installPackageOnDrop)
                        val installPackageState = rememberDropInstallPackageState()
                        val installPackageHandler = rememberInstallPackageDropHandler(installPackageState)
                        WindowDropHost(
                            handlers = listOfNotNull(

                                installPackageHandler.takeIf { installPackageOnDrop },
                            ),
                            Modifier.fillMaxSize(),
                        ) {
                            WynimeAppContent(wynimeNavigator)
                        }
                        InstallPackageDropDialogs(installPackageState)
                        Toast({ showing }, { Text(content) })
                    }
                }
            }
        }
    }
}

private fun saveWindowState(
    windowState: WindowState,
    update: (SavedWindowState) -> Unit,
) {
    val newState = SavedWindowState(
        x = windowState.position.x,
        y = windowState.position.y,
        width = windowState.size.width,
        height = windowState.size.height,
    )
    if (isWindowSizeValid(DpSize(width = newState.width, height = newState.height))) {
        update(newState)
    }
}

private fun restoreWindowState(
    windowState: WindowState,
    saved: SavedWindowState?,
) {
    if (saved == null) {
        return
    }

    val savedWindowPosition = WindowPosition(
        x = saved.x,
        y = saved.y,
    )
    val savedWindowSize = DpSize(
        width = saved.width,
        height = saved.height,
    )
    if (isWindowSizeValid(savedWindowSize)) {
        windowState.size = savedWindowSize
    }
    if (isWindowPositionValid(savedWindowPosition)) {
        windowState.position = savedWindowPosition
    }
}

private fun isWindowSizeValid(
    windowSize: DpSize,
    minimumSize: DpSize = DpSize(400.dp, 400.dp),
): Boolean = windowSize.width >= minimumSize.width && windowSize.height >= minimumSize.height

private fun isWindowPositionValid(
    windowPosition: WindowPosition,

    screenSize: DpSize = runCatching { ScreenUtils.getScreenSize() }.getOrElse { DpSize(1280.dp, 720.dp) },
): Boolean = (windowPosition.x > 0.dp && windowPosition.y > 0.dp
        && windowPosition.x < screenSize.width - 200.dp && windowPosition.y < screenSize.height - 200.dp)
