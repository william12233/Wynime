/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.app.data.network.AniCommentReportService
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.AniPersonCommentService
import me.him188.ani.app.data.network.AniSubjectRelationIndexService
import me.him188.ani.app.data.network.AniSubjectSearchService
import me.him188.ani.app.data.network.AnimeScheduleService
import me.him188.ani.app.data.network.BangumiApiProvider
import me.him188.ani.app.data.network.BangumiExploreDataSource
import me.him188.ani.app.data.network.BangumiScheduleService
import me.him188.ani.app.data.network.WynimeCloudClient
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.BangumiBangumiCommentServiceImpl
import me.him188.ani.app.data.network.BangumiCommentService
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.network.BangumiSubjectService
import me.him188.ani.app.data.network.EpisodeService
import me.him188.ani.app.data.network.EpisodeServiceImpl
import me.him188.ani.app.data.network.RemoteSubjectService
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.MIGRATION_19_20
import me.him188.ani.app.data.persistent.database.createDatabaseBuilder
import me.him188.ani.app.data.repository.player.PlaybackHistorySyncer
import me.him188.ani.app.data.repository.repositoryModules
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeature
import me.him188.ani.app.domain.foundation.ConvertSendCountExceedExceptionFeatureHandler
import me.him188.ani.app.domain.foundation.CookieJarFeatureHandler
import me.him188.ani.app.domain.foundation.WebSourceIdentityFeatureHandler
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider.HoldingInstanceMatrix
import me.him188.ani.app.domain.foundation.DefaultVersionExpiryService
import me.him188.ani.app.domain.foundation.DistributionChannelFeatureHandler
import me.him188.ani.app.domain.foundation.GlobalHttpEventBus
import me.him188.ani.app.domain.foundation.GlobalHttpEvents
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.ServerListFeature
import me.him188.ani.app.domain.foundation.ServerListFeatureConfig
import me.him188.ani.app.domain.foundation.ServerListFeatureHandler
import me.him188.ani.app.domain.foundation.SseFeatureHandler
import me.him188.ani.app.domain.foundation.UseAniTokenFeatureHandler
import me.him188.ani.app.domain.foundation.UserAgentFeature
import me.him188.ani.app.domain.foundation.UserAgentFeatureHandler
import me.him188.ani.app.domain.foundation.VersionExpiryFeatureHandler
import me.him188.ani.app.domain.foundation.VersionExpiryService
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.captcha.BrowserImageCaptchaSolver
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.MacCmsImageCaptchaSolver
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.engine.KtorPersistentHttpDownloader
import me.him188.ani.app.domain.media.cache.storage.HttpMediaCacheStorage
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManagerImpl
import me.him188.ani.app.domain.media.fetch.SubjectMediaFetchSessionRegistry
import me.him188.ani.app.domain.media.fetch.createFetchFetchSession
import me.him188.ani.app.domain.sourceplugin.InstalledSourcePluginRepository
import me.him188.ani.app.domain.sourceplugin.SourcePluginContextFactory
import me.him188.ani.app.domain.sourceplugin.SourcePluginHttpClient
import me.him188.ani.app.domain.sourceplugin.SourcePluginInstaller
import me.him188.ani.app.domain.sourceplugin.SourcePluginLoader
import me.him188.ani.app.domain.sourceplugin.SourcePluginRegistry
import me.him188.ani.app.domain.sourceplugin.SourcePluginRepositoryClient
import me.him188.ani.app.domain.sourceplugin.SourcePluginStorage
import me.him188.ani.app.domain.sourceplugin.createSourcePluginLoader
import me.him188.ani.app.domain.sourceplugin.currentSourcePluginPlatform
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.auth.WynimeCloudSessionRefresher
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.domain.settings.SettingsBasedProxyProvider
import me.him188.ani.app.domain.update.UpdateManager
import me.him188.ani.app.domain.usecase.useCaseModules
import me.him188.ani.app.ui.subject.details.state.DefaultSubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.BangumiClientImpl
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.coroutines.childScopeContext
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.source.plugin.api.SourceHttpClient
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module

private val Scope.client get() = get<BangumiClient>()
private val Scope.database get() = get<AniDatabase>()

/**
 * 各端共享的 Koin 装配，默认包含 HTTP 媒体缓存模块。
 *
 * [enableMediaCache] 为 false 时只绑定空存储的 [MediaDownloadManager]，不注册 [HttpDownloader]。
 */
fun KoinApplication.getCommonKoinModule(
    getContext: () -> Context,
    coroutineScope: CoroutineScope,
    enableMediaCache: Boolean = true,
) = listOf(
    useCaseModules(),
    repositoryModules(getContext),
    otherModules(getContext, coroutineScope, enableMediaCache),
)

private fun KoinApplication.otherModules(
    getContext: () -> Context,
    coroutineScope: CoroutineScope,
    enableMediaCache: Boolean,
) = module {
    // Application services
    single<ProxyProvider> { SettingsBasedProxyProvider(get(), coroutineScope) }
    single<SessionManager> {
        SessionManager(
            tokenRepository = get(),
            coroutineScope = coroutineScope,
            refreshSession = WynimeCloudSessionRefresher(get()),
        )
    }
    single<SessionStateProvider> {
        get<SessionManager>().stateProvider
    }
    single<HttpClientProvider> {
        DefaultHttpClientProvider(
            get(), coroutineScope,
            featureHandlers = listOf(
                UserAgentFeatureHandler,
                UseAniTokenFeatureHandler(flowOf(null), onRefresh = { null }),
                ServerListFeatureHandler(
                    flowOf(emptyList()),
                ),
                DistributionChannelFeatureHandler { currentAniBuildConfig.distroChannel },
                ConvertSendCountExceedExceptionFeatureHandler,
                VersionExpiryFeatureHandler,
                SseFeatureHandler,
                CookieJarFeatureHandler, // web 数据源统一 cookie jar (构造时注入)
                WebSourceIdentityFeatureHandler, // web 数据源 per-host UA 对齐
            ),
        )
    }
    // Web 数据源验证码处理 (docs/contributing/code/media/web-captcha.md)
    single<WebSourceCookieJar> { WebSourceCookieJar() }
    single<WebSourceIdentityRegistry> { WebSourceIdentityRegistry() }
    single<WebSessionManager> {
        val browserFactory = get<CaptchaBrowserFactory>()
        val evaluator = PageEvaluator()
        val recognizer = get<ImageCaptchaRecognizer>()
        val settingsRepository = get<SettingsRepository>()
        WebSessionManager(
            browserFactory = browserFactory,
            evaluator = evaluator,
            cookieJar = get(),
            identityRegistry = get(),
            client = get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.BROWSER,
                cookieJar = get(),
                identityRegistry = get(),
            ),
            backgroundScope = coroutineScope,
            solvers = listOf(
                MacCmsImageCaptchaSolver(recognizer),
                BrowserImageCaptchaSolver(recognizer),
            ),
            solverEnabled = {
                settingsRepository.mediaSelectorSettings.flow.first().enableImageCaptchaAutoSolve
            },
            maxSessions = browserFactory.recommendedMaxSessions,
        )
    }
    single<VersionExpiryService> { DefaultVersionExpiryService() }
    // Wire Global HTTP event bus to VersionExpiryService
    run {
        val service = koin.inject<VersionExpiryService>()
        GlobalHttpEventBus = object : GlobalHttpEvents {
            override fun onVersionExpired(latestVersion: String?) {
                service.value.onVersionExpired(latestVersion)
            }
        }
    }
    single<WynimeCloudClient> {
        WynimeCloudClient(
            get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.ANI,
                useAniToken = false,
            ),
        )
    }
    single<BangumiApiProvider> {
        BangumiApiProvider(
            client = get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.ANI,
                useAniToken = false,
            ),
            tokenRepository = get(),
        )
    }
    single<BangumiExploreDataSource> { get<BangumiApiProvider>() }
    single<BangumiClient> {
        BangumiClientImpl(
            get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.ANI,
            ),
        )
    }

    single<AniSubjectSearchService> {
        AniSubjectSearchService(
            bangumiApi = get(),
        )
    }

    // Data layer network services
    single<SubjectService> {
        BangumiSubjectService(get())
    }
    single<EpisodeService> { EpisodeServiceImpl(get()) }

    single<BangumiRelatedPeopleService> { BangumiRelatedPeopleService(get()) }
    single<BangumiCommentService> { BangumiBangumiCommentServiceImpl(get()) }
    single<AniEpisodeCommentService> {
        AniEpisodeCommentService(
            episodesApi = null,
            bangumiApi = get(),
        )
    }
    single<AniCommentReportService> { AniCommentReportService() }
    single<AniPersonCommentService> {
        AniPersonCommentService(bangumiApi = get())
    }
    single(createdAtStart = true) {
        PlaybackHistorySyncer(
            repository = get(),
            cloudClient = get(),
            tokenRepository = get(),
            settingsRepository = get(),
            sessionStateProvider = get(),
            scope = coroutineScope,
        ).also { it.start() }
    }
    single<AniSubjectRelationIndexService> {
        AniSubjectRelationIndexService(get())
    }

    single<AnimeScheduleService> { AnimeScheduleService() }
    single<BangumiScheduleService> { BangumiScheduleService(get()) }
    // TV 横版 backdrop / 分集剧照; 未配置 ani.tmdb.api.token 时自动关闭
    single<BangumiSummaryService> { BangumiSummaryService(get()) }

    single<UpdateManager> {
        UpdateManager(
            // Android FileProvider 共享整个 updates/ 目录, 见 file_paths.xml
            rootDir = getContext().files.cacheDir.resolve("updates"),
        )
    }

    // Executable source plugin host. The registry owns plugin instances; the media selector only
    // receives its read-only MediaSource adapters through the flow below.
    single<SourcePluginStorage> {
        SourcePluginStorage.defaultRoot(getContext().files.dataDir)
    }
    single<InstalledSourcePluginRepository> {
        InstalledSourcePluginRepository(getContext().dataStores.installedSourcePluginsStore)
    }
    single<SourceHttpClient> {
        SourcePluginHttpClient(
            get<HttpClientProvider>().get(ScopedHttpClientUserAgent.BROWSER),
        )
    }
    single<SourcePluginRepositoryClient> {
        SourcePluginRepositoryClient(get<SourceHttpClient>())
    }
    single<SourcePluginLoader> {
        createSourcePluginLoader(getContext())
    }
    single<SourcePluginContextFactory> {
        SourcePluginContextFactory(
            httpClientProvider = get(),
            platform = currentSourcePluginPlatform,
            hostVersion = currentAniBuildConfig.versionName,
            webSessionManager = get(),
            cookieJar = get(),
            identityRegistry = get(),
        )
    }
    single<SourcePluginInstaller> {
        SourcePluginInstaller(
            repositoryClient = get(),
            installedRepository = get(),
            storage = get(),
            platform = currentSourcePluginPlatform,
            hostVersion = currentAniBuildConfig.versionName,
        )
    }
    single<SourcePluginRegistry> {
        SourcePluginRegistry(
            installedRepository = get(),
            installer = get(),
            loader = get(),
            contextFactory = get(),
        )
    }

    single<AniDatabase> {
        getContext().createDatabaseBuilder()
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                startVersions = buildList {
                    addAll(1..15) // 16 is destructive
                }.toIntArray(),
            )
            .addMigrations(MIGRATION_19_20)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO_)
            .build()
    }

    single {
        DownloadOperations(
            downloadManager = get(),
            deleteCache = get(),
            executionScope = coroutineScope.childScope(),
        )
    }

    if (enableMediaCache) {
        single<HttpDownloader> {
            KtorPersistentHttpDownloader(
                dao = database.httpCacheDownloadStateDao(),
                get<HttpClientProvider>().get(),
                fileSystem = SystemFileSystem,
                baseSaveDir = get<MediaSaveDirProvider>().saveDir
                    .let { Path(it).resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR) },
                scope = coroutineScope,
            )
        }

        single<MediaDownloadManager> {
            val id = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID
            val metadataStore = getContext().dataStores.mediaCacheMetadataStore

            MediaDownloadManager(
                storages = listOf(
                    @Suppress("DEPRECATION")
                    HttpMediaCacheStorage(
                        mediaSourceId = id,
                        store = metadataStore,
                        dao = database.httpCacheDownloadStateDao(),
                        httpEngine = get<HttpMediaCacheEngine>(),
                        displayName = "LocalWebM3u",
                        coroutineScope.childScopeContext(),
                    ),
                ),
                backgroundScope = coroutineScope.childScope(),
            )
        }
    } else {
        single<MediaDownloadManager> {
            MediaDownloadManager(
                storages = emptyList(),
                backgroundScope = coroutineScope.childScope(),
            )
        }
    }

    // Media source services
    single<MediaSourceManager> {
        MediaSourceManagerImpl(
            additionalSources = {
                get<MediaDownloadManager>().storages.map { it.cacheMediaSource }
            },
            pluginSources = get<SourcePluginRegistry>().mediaSources,
        )
    }
    single {
        SubjectMediaFetchSessionRegistry(
            scope = coroutineScope,
            createSession = { request ->
                get<MediaSourceManager>().createFetchFetchSession(flowOf(request))
            },
        )
    }
    // Caching
    single<MeteredNetworkDetector> { createMeteredNetworkDetector(getContext()) }
    single<SubjectDetailsStateFactory> { DefaultSubjectDetailsStateFactory() }
}

/**
 * 会在非 preview 环境调用. 用来初始化一些模块
 */
fun KoinApplication.startCommonKoinModule(
    context: Context,
    coroutineScope: CoroutineScope,
): KoinApplication {
    // Start the proxy provider very soon (before initialization of any other components)
    runBlocking {
        koin.get<SessionManager>().clearSessionIfAccessTokenExpired()
        // We have to block here to read the saved proxy settings
        when (val proxyProvider = koin.get<HttpClientProvider>()) {
            // compile-safe type cast
            is DefaultHttpClientProvider -> proxyProvider.startProxyListening(holdingInstanceMatrixSequence())
        }
    }
    // Now, the proxy settings is ready. Other components can use http clients.

    coroutineScope.launch {
        // TV 不装配缓存模块: HttpDownloader 无绑定时跳过; 空引擎 MediaDownloadManager 的循环自然为空.
        koin.getOrNull<HttpDownloader>()?.init() // restore http download states first
        koin.getOrNull<MediaDownloadManager>()?.let { manager ->
            for (storage in manager.storages) {
                storage.restorePersistedCaches()
            }
        }
    }

    coroutineScope.launch {
        koin.get<SourcePluginRegistry>().loadInstalled()
    }

    koin.get<SessionManager>().startBackgroundJob()
    return this
}

/**
 * 需要一直持有的 http client 实例列表
 */
private fun holdingInstanceMatrixSequence() = sequence {
    for (userAgent in ScopedHttpClientUserAgent.entries) {
        yield(
            HoldingInstanceMatrix(
                setOf(
                    UserAgentFeature.withValue(userAgent),
                    ServerListFeature.withValue(ServerListFeatureConfig(aniServerRules = null)),
                    ConvertSendCountExceedExceptionFeature.withValue(true),
                ),
            ),
        )
    }

    yield(
        HoldingInstanceMatrix(
            setOf(
                UserAgentFeature.withValue(ScopedHttpClientUserAgent.ANI),
                ServerListFeature.withValue(ServerListFeatureConfig(aniServerRules = null)),
                ConvertSendCountExceedExceptionFeature.withValue(true),
            ),
        ),
    )
}

fun createAppRootCoroutineScope(): CoroutineScope {
    val logger = logger("ani-root")
    return CoroutineScope(
        CoroutineExceptionHandler { coroutineContext, throwable ->
            logger.warn(throwable) {
                "Uncaught exception in coroutine $coroutineContext"
            }
        } + SupervisorJob() + Dispatchers.Default,
    )
}
