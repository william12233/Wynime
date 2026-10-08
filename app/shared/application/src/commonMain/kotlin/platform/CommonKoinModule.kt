package com.wynime.app.platform

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import com.wynime.app.data.network.CollectionRemovalService
import com.wynime.app.data.network.WynimeCommentReportService
import com.wynime.app.data.network.WynimeEpisodeCommentService
import com.wynime.app.data.network.WynimePersonCommentService
import com.wynime.app.data.network.WynimeSubjectRelationIndexService
import com.wynime.app.data.network.WynimeSubjectSearchService
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.network.BangumiCalendarRepository
import com.wynime.app.data.network.BangumiExploreDataSource
import com.wynime.app.data.network.BangumiScheduleService
import com.wynime.app.data.network.WynimeCloudClient
import com.wynime.app.data.network.BangumiSummaryService
import com.wynime.app.data.network.BangumiBangumiCommentServiceImpl
import com.wynime.app.data.network.BangumiCommentService
import com.wynime.app.data.network.BangumiRelatedPeopleService
import com.wynime.app.data.network.BangumiSubjectService
import com.wynime.app.data.network.EpisodeService
import com.wynime.app.data.network.EpisodeServiceImpl
import com.wynime.app.data.network.SubjectService
import com.wynime.app.data.persistent.dataStores
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.persistent.database.MIGRATION_19_20
import com.wynime.app.data.persistent.database.createDatabaseBuilder
import com.wynime.app.data.repository.player.PlaybackHistorySyncer
import com.wynime.app.data.repository.repositoryModules
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import com.wynime.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCaseImpl
import com.wynime.app.navigation.BrowserNavigator
import com.wynime.app.navigation.OpenBrowserResult
import kotlinx.coroutines.withContext
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.domain.foundation.ConvertSendCountExceedExceptionFeature
import com.wynime.app.domain.foundation.ConvertSendCountExceedExceptionFeatureHandler
import com.wynime.app.domain.foundation.CookieJarFeatureHandler
import com.wynime.app.domain.foundation.WebSourceIdentityFeatureHandler
import com.wynime.app.domain.foundation.DefaultHttpClientProvider
import com.wynime.app.domain.foundation.DefaultHttpClientProvider.HoldingInstanceMatrix
import com.wynime.app.domain.foundation.DefaultVersionExpiryService
import com.wynime.app.domain.foundation.DistributionChannelFeatureHandler
import com.wynime.app.domain.foundation.GlobalHttpEventBus
import com.wynime.app.domain.foundation.GlobalHttpEvents
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.ScopedHttpClientUserAgent
import com.wynime.app.domain.foundation.ServerListFeature
import com.wynime.app.domain.foundation.ServerListFeatureConfig
import com.wynime.app.domain.foundation.ServerListFeatureHandler
import com.wynime.app.domain.foundation.SseFeatureHandler
import com.wynime.app.domain.foundation.UserAgentFeature
import com.wynime.app.domain.foundation.UserAgentFeatureHandler
import com.wynime.app.domain.foundation.VersionExpiryFeatureHandler
import com.wynime.app.domain.foundation.VersionExpiryService
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.foundation.withValue
import com.wynime.app.domain.media.download.DownloadOperations
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.mediasource.web.PageEvaluator
import com.wynime.app.domain.mediasource.web.captcha.BrowserImageCaptchaSolver
import com.wynime.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import com.wynime.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import com.wynime.app.domain.mediasource.web.captcha.MacCmsImageCaptchaSolver
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.wynime.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import com.wynime.app.domain.media.cache.engine.HttpMediaCacheEngine
import com.wynime.app.domain.media.cache.engine.KtorPersistentHttpDownloader
import com.wynime.app.domain.media.cache.storage.HttpMediaCacheStorage
import com.wynime.app.domain.media.cache.storage.MediaSaveDirProvider
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.fetch.MediaSourceManagerImpl
import com.wynime.app.domain.media.fetch.SubjectMediaFetchSessionRegistry
import com.wynime.app.domain.media.fetch.createFetchFetchSession
import com.wynime.app.domain.sourceplugin.InstalledSourcePluginRepository
import com.wynime.app.domain.sourceplugin.SourcePluginContextFactory
import com.wynime.app.domain.sourceplugin.SourcePluginHttpClient
import com.wynime.app.domain.sourceplugin.SourcePluginInstaller
import com.wynime.app.domain.sourceplugin.SourcePluginLoader
import com.wynime.app.domain.sourceplugin.ResourceSourcePluginPackages
import com.wynime.app.domain.sourceplugin.SourcePluginRegistry
import com.wynime.app.domain.sourceplugin.SourcePluginRepositoryClient
import com.wynime.app.domain.sourceplugin.SourcePluginStorage
import com.wynime.app.domain.sourceplugin.createSourcePluginLoader
import com.wynime.app.domain.sourceplugin.currentSourcePluginPlatform
import com.wynime.app.domain.session.SessionManager
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.domain.session.auth.WynimeCloudSessionRefresher
import com.wynime.app.domain.settings.ProxyProvider
import com.wynime.app.domain.settings.SettingsBasedProxyProvider
import com.wynime.app.domain.update.UpdateManager
import com.wynime.app.domain.usecase.useCaseModules
import com.wynime.app.ui.subject.details.state.DefaultSubjectDetailsStateFactory
import com.wynime.app.ui.subject.details.state.SubjectDetailsStateFactory
import com.wynime.datasources.bangumi.BangumiClient
import com.wynime.datasources.bangumi.BangumiClientImpl
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.coroutines.childScopeContext
import com.wynime.utils.httpdownloader.HttpDownloader
import com.wynime.utils.io.resolve
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.source.plugin.api.SourceHttpClient
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module

private val Scope.client get() = get<BangumiClient>()
private val Scope.database get() = get<WynimeDatabase>()

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
        val tokenRepository = get<TokenRepository>()
        DefaultHttpClientProvider(
            get(), coroutineScope,
            featureHandlers = listOf(
                UserAgentFeatureHandler,
                ServerListFeatureHandler(
                    flowOf(emptyList()),
                ),
                DistributionChannelFeatureHandler { currentWynimeBuildConfig.distroChannel },
                ConvertSendCountExceedExceptionFeatureHandler,
                VersionExpiryFeatureHandler,
                SseFeatureHandler,
                CookieJarFeatureHandler,
                WebSourceIdentityFeatureHandler,
            ),
        )
    }

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
                userAgent = ScopedHttpClientUserAgent.WYNIME,
            ),
        )
    }
    single { CollectionRemovalService(get(), get()) }
    single<SetSubjectCollectionTypeOrDeleteUseCase> {
        val removal = get<CollectionRemovalService>()
        val browser = get<BrowserNavigator>()
        SetSubjectCollectionTypeOrDeleteUseCaseImpl(get()) { subjectId ->
            val url = removal.start(subjectId)
            val result = withContext(Dispatchers.Main) { browser.openBrowser(getContext(), url) }
            if (result is OpenBrowserResult.Failure) {
                throw RepositoryRequestError("無法開啟 Bangumi 網頁，取消收藏尚未送出", cause = result.throwable)
            }
        }
    }
    single<BangumiApiProvider> {
        BangumiApiProvider(
            client = get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.WYNIME,
            ),
            tokenRepository = get(),
        )
    }
    single<BangumiExploreDataSource> { get<BangumiApiProvider>() }
    single<BangumiClient> {
        BangumiClientImpl(
            get<HttpClientProvider>().get(
                userAgent = ScopedHttpClientUserAgent.WYNIME,
            ),
        )
    }

    single<WynimeSubjectSearchService> {
        WynimeSubjectSearchService(
            bangumiApi = get(),
        )
    }

    single<SubjectService> {
        BangumiSubjectService(
            bangumiApi = get(),
            collectionRemovalService = get(),
        )
    }
    single<EpisodeService> { EpisodeServiceImpl(get()) }

    single<BangumiRelatedPeopleService> { BangumiRelatedPeopleService(get()) }
    single<BangumiCommentService> { BangumiBangumiCommentServiceImpl(get()) }
    single<WynimeEpisodeCommentService> {
        WynimeEpisodeCommentService(
            bangumiApi = get(),
        )
    }
    single<WynimeCommentReportService> { WynimeCommentReportService() }
    single<WynimePersonCommentService> {
        WynimePersonCommentService(bangumiApi = get())
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
    single<WynimeSubjectRelationIndexService> {
        WynimeSubjectRelationIndexService(get())
    }

    single<BangumiScheduleService> {
        BangumiScheduleService(
            dataSource = get(),
            calendarRepository = get<BangumiCalendarRepository>(),
        )
    }

    single<BangumiSummaryService> { BangumiSummaryService(get()) }

    single<UpdateManager> {
        UpdateManager(

            rootDir = getContext().files.cacheDir.resolve("updates"),
        )
    }

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
            hostVersion = currentWynimeBuildConfig.versionName,
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
            hostVersion = currentWynimeBuildConfig.versionName,
        )
    }
    single<SourcePluginRegistry> {
        SourcePluginRegistry(
            installedRepository = get(),
            installer = get(),
            loader = get(),
            contextFactory = get(),
            bundledPackages = ResourceSourcePluginPackages(currentSourcePluginPlatform),
        )
    }

    single<WynimeDatabase> {
        getContext().createDatabaseBuilder()
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                startVersions = buildList {
                    addAll(1..15)
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

    single<MeteredNetworkDetector> { createMeteredNetworkDetector(getContext()) }
    single<SubjectDetailsStateFactory> { DefaultSubjectDetailsStateFactory() }
}

fun KoinApplication.startCommonKoinModule(
    context: Context,
    coroutineScope: CoroutineScope,
): KoinApplication {

    runBlocking {
        koin.get<SessionManager>().clearSessionIfAccessTokenExpired()

        when (val proxyProvider = koin.get<HttpClientProvider>()) {

            is DefaultHttpClientProvider -> proxyProvider.startProxyListening(holdingInstanceMatrixSequence())
        }
    }

    coroutineScope.launch {

        koin.getOrNull<HttpDownloader>()?.init()
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

private fun holdingInstanceMatrixSequence() = sequence {
    for (userAgent in ScopedHttpClientUserAgent.entries) {
        yield(
            HoldingInstanceMatrix(
                setOf(
                    UserAgentFeature.withValue(userAgent),
                    ServerListFeature.withValue(ServerListFeatureConfig(serviceServerRules = null)),
                    ConvertSendCountExceedExceptionFeature.withValue(true),
                ),
            ),
        )
    }

    yield(
        HoldingInstanceMatrix(
            setOf(
                UserAgentFeature.withValue(ScopedHttpClientUserAgent.WYNIME),
                ServerListFeature.withValue(ServerListFeatureConfig(serviceServerRules = null)),
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
