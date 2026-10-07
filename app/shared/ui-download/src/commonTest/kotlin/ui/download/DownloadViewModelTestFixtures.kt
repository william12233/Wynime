package com.wynime.app.ui.download

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.player.EpisodeHistory
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
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.createTestSubjectCollection
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.data.repository.player.PlaybackHistoryPendingOp
import com.wynime.app.data.repository.subject.CollectionsFilterQuery
import com.wynime.app.data.repository.subject.OfflineSubjectDisplayInfo
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.user.Settings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.cache.DeleteCacheUseCase
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.MediaCacheState
import com.wynime.app.domain.media.cache.TestMediaCache
import com.wynime.app.domain.media.cache.engine.DummyMediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaCacheEngine
import com.wynime.app.domain.media.cache.engine.MediaStats
import com.wynime.app.domain.media.cache.storage.MediaCacheStorage
import com.wynime.app.domain.media.download.AddDownloadUseCase
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.fetch.CompletedConditions
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaFetcher
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.MediaSelectorFactory
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.mediasource.instance.MediaSourceInstance
import com.wynime.app.domain.mediasource.instance.MediaSourceSave
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import com.wynime.datasources.api.source.FactoryId
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceConfig
import com.wynime.datasources.api.source.MediaSourceFactory
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

internal const val TEST_STORAGE_ID = "test-storage"

@OptIn(TestOnly::class)
internal fun testDownloadCache(
    episodeId: Int,
    subjectId: Int = 1,
    creationTime: Long = episodeId * 100L,
    state: MediaCacheState = MediaCacheState.IN_PROGRESS,
    range: EpisodeRange? = null,
): TestMediaCache {
    val media = TestMediaList.first().copy(mediaId = "media-$subjectId-$episodeId", episodeRange = range)
    return TestMediaCache(
        CachedMedia(media, TEST_STORAGE_ID, ResourceLocation.LocalFile("/download-$subjectId-$episodeId")),
        MediaCacheMetadata(
            subjectId = subjectId.toString(),
            episodeId = episodeId.toString(),
            subjectNames = listOf("Subject $subjectId"),
            episodeSort = EpisodeSort(episodeId),
            episodeName = "Episode $episodeId",
            creationTime = creationTime,
        ),
    ).apply { this.state.value = state }
}

internal class FakeDownloadStorage(
    initialStats: MediaStats = MediaStats.Zero,
    override val engine: MediaCacheEngine = DummyMediaCacheEngine(TEST_STORAGE_ID),
) : MediaCacheStorage {
    override val mediaSourceId: String = TEST_STORAGE_ID
    override val cacheMediaSource: MediaSource get() = error("Not used")
    override val listFlow = MutableStateFlow<List<MediaCache>>(emptyList())
    override val stats = MutableStateFlow(initialStats)

    override suspend fun restorePersistedCaches() = Unit

    override suspend fun cache(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        resume: Boolean,
    ): MediaCache = error("Not used")

    override suspend fun deleteFirst(predicate: (MediaCache) -> Boolean): Boolean {
        val selected = listFlow.value.firstOrNull(predicate) ?: return false
        listFlow.value -= selected
        return true
    }

    override fun close() = Unit
}

internal class FakeDeleteCacheUseCase(private val downloadManager: MediaDownloadManager) : DeleteCacheUseCase {
    var failure: Throwable? = null

    override suspend fun invoke(cache: MediaCache) {
        failure?.let { throw it }
        downloadManager.deleteDownload(cache)
    }
}

internal class FakeSubjectCollectionRepository : SubjectCollectionRepository() {

    val collection = MutableStateFlow<SubjectCollectionInfo?>(null)

    var collectionFailure: Throwable? = null
    val collectionTypes = mutableMapOf<Int, UnifiedCollectionType?>()
    val displayInfos = mutableMapOf<Int, OfflineSubjectDisplayInfo?>()

    override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = flow {
        collectionFailure?.let { throw it }
        emitAll(collection.filterNotNull())
    }

    override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> =
        flowOf(collectionTypes[subjectId])

    override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> =
        flowOf(displayInfos[subjectId])

    override suspend fun invalidateAllCaches() = throw UnsupportedOperationException()
    override suspend fun invalidateCache(subjectIds: List<Int>) = throw UnsupportedOperationException()
    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> = throw UnsupportedOperationException()
    override fun subjectCollectionsPager(
        query: CollectionsFilterQuery,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<SubjectCollectionInfo>> = throw UnsupportedOperationException()

    override fun cachedValidSubjectIds(): Flow<List<Int>> = throw UnsupportedOperationException()
    override suspend fun updateRecentlyUpdatedSubjectCollections(limit: Int, type: UnifiedCollectionType?, offset: Int) =
        throw UnsupportedOperationException()

    override fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>?,
    ): Flow<List<SubjectCollectionInfo>> = throw UnsupportedOperationException()

    override suspend fun updateRating(subjectId: Int, score: Int?, comment: String?, tags: List<String>?, isPrivate: Boolean?) =
        throw UnsupportedOperationException()

    override suspend fun setSubjectCollectionTypeOrDelete(subjectId: Int, type: UnifiedCollectionType?) =
        throw UnsupportedOperationException()

    override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> =
        throw UnsupportedOperationException()

    override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> =
        throw UnsupportedOperationException()

    override suspend fun performBangumiFullSync() = throw UnsupportedOperationException()
    override suspend fun getBangumiFullSyncState(): BangumiSyncState? = throw UnsupportedOperationException()
}

@OptIn(TestOnly::class)
internal fun testSubjectCollection(subjectId: Int = 1, episodeCount: Int = 3): SubjectCollectionInfo =
    createTestSubjectCollection(
        subjectId,
        (1..episodeCount).map { id ->
            EpisodeCollectionInfo(
                EpisodeInfo(episodeId = id, type = EpisodeType.MainStory, name = "Episode $id", sort = EpisodeSort(id), ep = EpisodeSort(id)),
                UnifiedCollectionType.NOT_COLLECTED,
            )
        },
        UnifiedCollectionType.DOING,
    )

internal class FakeEpisodePlayHistoryRepository : EpisodePlayHistoryRepository {
    override val flow = MutableStateFlow<List<EpisodeHistory>>(emptyList())
    override val allHistoriesFlow: Flow<List<EpisodeHistory>> get() = flow
    override fun flowByEpisodeIds(episodeIds: Collection<Int>): Flow<List<EpisodeHistory>> =
        flow.map { histories -> histories.filter { it.episodeId in episodeIds } }
    override val pendingOpsFlow: Flow<List<PlaybackHistoryPendingOp>> get() = throw UnsupportedOperationException()
    override val lastSyncAtMillisFlow: Flow<Long> get() = throw UnsupportedOperationException()
    override suspend fun clear() = throw UnsupportedOperationException()
    override suspend fun remove(episodeId: Int) = throw UnsupportedOperationException()
    override suspend fun removeAll(episodeIds: Collection<Int>) = throw UnsupportedOperationException()
    override suspend fun saveOrUpdate(
        episodeId: Int,
        positionMillis: Long,
        subjectId: Int?,
        episodeSort: Float?,
        subjectName: String?,
        subjectImageUrl: String?,
        episodeName: String?,
        durationMillis: Long?,
    ) = throw UnsupportedOperationException()

    override suspend fun applySyncResult(sentPendingOpIds: Collection<Long>, records: List<EpisodeHistory>, nextSyncAtMillis: Long) =
        throw UnsupportedOperationException()

    override suspend fun deletePendingOps(ids: Collection<Long>) = throw UnsupportedOperationException()
    override suspend fun getPositionMillisByEpisodeId(episodeId: Int): Long? = throw UnsupportedOperationException()
}

internal class FakeSettingsRepository : SettingsRepository {
    override val mediaSelectorSettings: Settings<MediaSelectorSettings> = object : Settings<MediaSelectorSettings> {
        private val state = MutableStateFlow(MediaSelectorSettings.Default)
        override val flow: Flow<MediaSelectorSettings> = state
        override suspend fun set(value: MediaSelectorSettings) {
            state.value = value
        }
    }

    override val defaultMediaPreference: Settings<MediaPreference> get() = error("Not used")
    override val profileSettings: Settings<ProfileSettings> get() = error("Not used")
    override val proxySettings: Settings<ProxySettings> get() = error("Not used")
    override val mediaCacheSettings: Settings<MediaCacheSettings> get() = error("Not used")
    override val uiSettings: Settings<UISettings> get() = error("Not used")
    override val themeSettings: Settings<ThemeSettings> get() = error("Not used")
    override val updateSettings: Settings<UpdateSettings> get() = error("Not used")
    override val videoScaffoldConfig: Settings<VideoScaffoldConfig> get() = error("Not used")
    override val playerKernelConfig: Settings<PlayerKernelConfig> get() = error("Not used")
    override val videoResolverSettings: Settings<VideoResolverSettings> get() = error("Not used")
    override val oneshotActionConfig: Settings<OneshotActionConfig> get() = error("Not used")
    override val analyticsSettings: Settings<AnalyticsSettings> get() = error("Not used")
    override val debugSettings: Settings<DebugSettings> get() = error("Not used")
}

@OptIn(TestOnly::class)
internal class FakeMediaFetcher : MediaFetcher {
    val releasedEpisodeIds = mutableSetOf<Int>()

    var mediaListFor: (episodeId: Int) -> List<Media> = { episodeId ->
        TestMediaList.map { it.copy(episodeRange = EpisodeRange.single(EpisodeSort(episodeId))) }
    }

    override fun newSession(requestLazy: Flow<MediaFetchRequest>, flowContext: CoroutineContext): MediaFetchSession =
        object : MediaFetchSession {
            override val request: Flow<MediaFetchRequest> = requestLazy
            override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()
            override val cumulativeResults: Flow<List<Media>> = flow {
                val episodeId = requestLazy.first().episodeId.toInt()
                emit(mediaListFor(episodeId))
                try {
                    awaitCancellation()
                } finally {
                    releasedEpisodeIds += episodeId
                }
            }
            override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)
            override fun setFetchRequest(request: MediaFetchRequest) = Unit
        }
}

internal class FakeMediaSourceManager(val fetcher: FakeMediaFetcher = FakeMediaFetcher()) : MediaSourceManager {
    override val allInstances: Flow<List<MediaSourceInstance>> = flowOf(emptyList())
    override val allFactories: List<MediaSourceFactory> = emptyList()
    override val allFactoryIds: List<FactoryId> = emptyList()
    override val mediaFetcher: Flow<MediaFetcher> = flowOf(fetcher)
    override val webVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(flowOf(emptyList<MediaSource>()))

    override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)
    override suspend fun addInstance(instanceId: String, mediaSourceId: String, factoryId: FactoryId, config: MediaSourceConfig) =
        error("Not used")

    override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> = error("Not used")
    override suspend fun partiallyReorderInstances(instanceIds: List<String>) = error("Not used")
    override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean = error("Not used")
    override suspend fun setEnabled(instanceId: String, enabled: Boolean) = error("Not used")
    override suspend fun removeInstance(instanceId: String) = error("Not used")
    override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = flowOf(MediaSelectorSourceTiers.Empty)
}

internal fun fakeMediaSelectorFactory(): MediaSelectorFactory = object : MediaSelectorFactory {
    override fun create(
        subjectId: Int,
        episodeId: Int,
        mediaList: Flow<List<Media>>,
        flowCoroutineContext: CoroutineContext,
        fetchRequest: Flow<MediaFetchRequest>?,
    ): MediaSelector = DefaultMediaSelector(
        mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
        mediaListNotCached = mediaList,
        savedUserPreference = flowOf(MediaPreference.Empty),
        savedDefaultPreference = flowOf(MediaPreference.Empty),
        mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
        flowCoroutineContext = EmptyCoroutineContext,
        enableCaching = false,
    )
}

internal class FakeEpisodePreferencesRepository : EpisodePreferencesRepository {
    val savedSubjectIds = mutableListOf<Int>()

    override fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference> = flowOf(MediaPreference.Empty)
    override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
        savedSubjectIds += subjectId
    }

    override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) = error("Not used")
    override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> = error("Not used")
    override suspend fun removePreferredWebMediaSource(subjectId: Int) = error("Not used")
}

internal class FakeAddDownloadUseCase(private val storage: FakeDownloadStorage) : AddDownloadUseCase {
    val createdEpisodeIds = mutableListOf<Int>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null

    override suspend fun invoke(subject: SubjectInfo, episode: EpisodeInfo, media: Media, metadata: MediaCacheMetadata): MediaCache {
        gate?.await()
        failure?.let { throw it }
        val cache = testDownloadCache(episode.episodeId, subject.subjectId)
        createdEpisodeIds += episode.episodeId
        storage.listFlow.value += cache
        return cache
    }
}
