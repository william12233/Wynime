package com.wynime.app.ui.subject.collection

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.preference.AnalyticsSettings
import com.wynime.app.data.models.preference.DebugSettings
import com.wynime.app.data.models.preference.MediaCacheSettings
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.preference.OneshotActionConfig
import com.wynime.app.data.models.preference.PlayerKernelConfig
import com.wynime.app.data.models.preference.ProfileSettings
import com.wynime.app.data.models.preference.ProxySettings
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.data.models.preference.UISettings
import com.wynime.app.data.models.preference.UpdateSettings
import com.wynime.app.data.models.preference.VideoResolverSettings
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.models.subject.SubjectCollectionCounts
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.network.EpisodeServiceImpl
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.persistent.database.WynimeDatabaseConstructor
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.episode.EpisodeProgressRepository
import com.wynime.app.data.repository.subject.CollectionsFilterQuery
import com.wynime.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import com.wynime.app.data.repository.subject.OfflineSubjectDisplayInfo
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.app.data.repository.user.Settings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.session.SessionEvent
import com.wynime.app.domain.session.SessionState
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.ktor.asScopedHttpClient
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class, TestOnly::class)
class UserCollectionsViewModelTest {

    private class FakeSubjectCollectionRepository : SubjectCollectionRepository() {

        val pagerCalls = AtomicInteger(0)

        val countsCollected = AtomicInteger(0)

        fun invalidate() = notifyCollectionsInvalidated()

        override fun subjectCollectionsPager(
            query: CollectionsFilterQuery,
            pagingConfig: PagingConfig,
        ): Flow<PagingData<SubjectCollectionInfo>> {
            pagerCalls.incrementAndGet()
            return flowOf(PagingData.empty())
        }

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = flow {
            countsCollected.incrementAndGet()
            emit(null)
        }

        override suspend fun invalidateAllCaches() = invalidate()

        override suspend fun invalidateCache(subjectIds: List<Int>) = invalidate()

        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> =
            throw UnsupportedOperationException()

        override fun cachedValidSubjectIds(): Flow<List<Int>> = throw UnsupportedOperationException()

        override suspend fun updateRecentlyUpdatedSubjectCollections(
            limit: Int,
            type: UnifiedCollectionType?,
            offset: Int,
        ) = throw UnsupportedOperationException()

        override fun mostRecentlyUpdatedSubjectCollectionsFlow(
            limit: Int,
            types: List<UnifiedCollectionType>?,
        ): Flow<List<SubjectCollectionInfo>> = throw UnsupportedOperationException()

        override suspend fun updateRating(
            subjectId: Int,
            score: Int?,
            comment: String?,
            tags: List<String>?,
            isPrivate: Boolean?,
        ) = throw UnsupportedOperationException()

        override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) =
            throw UnsupportedOperationException()

        override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> =
            throw UnsupportedOperationException()

        override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> =
            throw UnsupportedOperationException()

        override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
            throw UnsupportedOperationException()

        override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
            throw UnsupportedOperationException()

        override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()

        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
    }

    private class FakeSessionStateProvider : SessionStateProvider {
        val events = MutableSharedFlow<SessionEvent>()
        override val stateFlow: Flow<SessionState> = MutableStateFlow(SessionState.Valid(bangumiConnected = true))
        override val eventFlow: Flow<SessionEvent> = events
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val uiSettings: Settings<UISettings> = object : Settings<UISettings> {
            private val state = MutableStateFlow(UISettings.Default)
            override val flow: Flow<UISettings> = state
            override suspend fun set(value: UISettings) {
                state.value = value
            }
        }

        override val mediaSelectorSettings: Settings<MediaSelectorSettings> by lazy { error("not implemented") }
        override val defaultMediaPreference: Settings<MediaPreference> by lazy { error("not implemented") }
        override val profileSettings: Settings<ProfileSettings> by lazy { error("not implemented") }
        override val proxySettings: Settings<ProxySettings> by lazy { error("not implemented") }
        override val mediaCacheSettings: Settings<MediaCacheSettings> by lazy { error("not implemented") }
        override val themeSettings: Settings<ThemeSettings> by lazy { error("not implemented") }
        override val updateSettings: Settings<UpdateSettings> by lazy { error("not implemented") }
        override val videoScaffoldConfig: Settings<VideoScaffoldConfig> by lazy { error("not implemented") }
        override val playerKernelConfig: Settings<PlayerKernelConfig> by lazy { error("not implemented") }
        override val videoResolverSettings: Settings<VideoResolverSettings> by lazy { error("not implemented") }
        override val oneshotActionConfig: Settings<OneshotActionConfig> by lazy { error("not implemented") }
        override val analyticsSettings: Settings<AnalyticsSettings> by lazy { error("not implemented") }
        override val debugSettings: Settings<DebugSettings> by lazy { error("not implemented") }
    }

    private lateinit var database: WynimeDatabase
    private lateinit var episodeHttpClient: HttpClient
    private lateinit var repository: FakeSubjectCollectionRepository
    private lateinit var sessionStateProvider: FakeSessionStateProvider
    private val fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() {

        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder<WynimeDatabase> { WynimeDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        repository = FakeSubjectCollectionRepository()
        sessionStateProvider = FakeSessionStateProvider()
        episodeHttpClient = HttpClient(MockEngine { error("Episode API not expected in this test") }) {
            expectSuccess = true
        }

        val episodeCollectionRepository = EpisodeCollectionRepository(
            subjectDao = database.subjectCollection(),
            episodeCollectionDao = database.episodeCollection(),
            episodeService = EpisodeServiceImpl(
                BangumiApiProvider(
                    client = episodeHttpClient.asScopedHttpClient(),
                    tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial)),
                ),
            ),
            subjectCollectionRepository = lazy { repository },
            getEpisodeTypeFiltersUseCase = GetEpisodeTypeFiltersUseCase { flowOf(EpisodeType.entries) },
        )
        val episodeProgressRepository = EpisodeProgressRepository(
            episodeCollectionRepository,
            MediaDownloadManager(emptyList(), fixtureScope),
        )
        startKoin {
            modules(
                module {
                    single<SubjectCollectionRepository> { repository }
                    single<SessionStateProvider> { sessionStateProvider }
                    single<SettingsRepository> { FakeSettingsRepository() }
                    single<EpisodeProgressRepository> { episodeProgressRepository }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        fixtureScope.cancel()
        episodeHttpClient.close()
        database.close()
        Dispatchers.resetMain()
    }

    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    private fun CoroutineScope.collectFirstTab(vm: UserCollectionsViewModel): Job {
        val items = vm.state.getCollectionLazyPagingItems(0)
        return launch { items.collectPagingData() }
    }

    private suspend fun awaitAtLeast(expected: Int, counter: AtomicInteger, what: String) = withTimeout(10.seconds) {
        while (counter.get() < expected) delay(20)
        assertEquals(expected, counter.get(), what)
    }

    private fun runViewModelTest(block: suspend CoroutineScope.(UserCollectionsViewModel) -> Unit) = runBlocking {

        val vm = UserCollectionsViewModel()
        try {
            val collector = collectFirstTab(vm)
            try {
                awaitAtLeast(1, repository.pagerCalls, "pager created once on first collection")
                awaitAtLeast(1, repository.countsCollected, "counts collected once at construction")
                block(vm)
            } finally {
                collector.cancel()
            }
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    @Test
    fun `COLL-VM-01 collectionsInvalidated 时重建分页器并重新拉取数量 - 构造即订阅, 无需 remember`() = runViewModelTest { _ ->

        withTimeout(10.seconds) { repository.collectionsInvalidatedSubscriptionCount.first { it >= 1 } }
        assertEquals(1, repository.pagerCalls.get())
        assertEquals(1, repository.countsCollected.get())

        repository.invalidate()

        awaitAtLeast(2, repository.pagerCalls, "pager rebuilt after invalidation")
        awaitAtLeast(2, repository.countsCollected, "counts re-collected after invalidation")
    }

    @Test
    fun `COLL-VM-02 NewLogin 时重建分页器并重新拉取数量`() = runViewModelTest { _ ->
        withTimeout(10.seconds) { sessionStateProvider.events.subscriptionCount.first { it >= 1 } }
        assertEquals(1, repository.pagerCalls.get())

        sessionStateProvider.events.emit(SessionEvent.NewLogin)

        awaitAtLeast(2, repository.pagerCalls, "pager rebuilt after new login")
        awaitAtLeast(2, repository.countsCollected, "counts re-collected after new login")
    }
}
