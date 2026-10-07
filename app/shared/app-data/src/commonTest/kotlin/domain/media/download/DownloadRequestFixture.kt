@file:OptIn(TestOnly::class)

package com.wynime.app.domain.media.download

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectCollectionCounts
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.createTestSubjectCollection
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.data.repository.subject.CollectionsFilterQuery
import com.wynime.app.data.repository.subject.OfflineSubjectDisplayInfo
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.cache.TestMediaCache
import com.wynime.app.domain.media.fetch.CompletedConditions
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaFetcher
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.MediaSelectorFactory
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.mediasource.instance.MediaSourceInstance
import com.wynime.app.domain.mediasource.instance.MediaSourceSave
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.DefaultMedia
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

internal fun requestTestEpisode(id: Int): EpisodeInfo = EpisodeInfo(
    episodeId = id,
    type = EpisodeType.MainStory,
    name = "Episode $id",
    sort = EpisodeSort(id),
    ep = EpisodeSort(id),
)

internal fun requestTestSubject(subjectId: Int = 1, episodeIds: Iterable<Int> = 1..6): SubjectCollectionInfo =
    createTestSubjectCollection(
        subjectId,
        episodeIds.map { EpisodeCollectionInfo(requestTestEpisode(it), UnifiedCollectionType.NOT_COLLECTED) },
        UnifiedCollectionType.DOING,
    )

internal fun requestTestMedia(id: Int, range: EpisodeRange? = EpisodeRange.single(EpisodeSort(id))): DefaultMedia =
    TestMediaList.first().copy(mediaId = "request-media-$id", episodeRange = range)

internal fun requestTestCache(media: Media, subjectId: Int, episodeId: Int): TestMediaCache = TestMediaCache(
    CachedMedia(media, DownloadRequestFixture.STORAGE_ID, ResourceLocation.LocalFile("/download-$episodeId")),
    MediaCacheMetadata(
        subjectId = subjectId.toString(),
        episodeId = episodeId.toString(),
        subjectNames = listOf("Subject $subjectId"),
        episodeSort = EpisodeSort(episodeId),
        episodeName = "Episode $episodeId",
    ),
)

internal class DownloadRequestFixture(
    val testScope: TestScope,
    supervisedApplication: Boolean = true,
) {
    private val applicationJob = testScope.backgroundScope.coroutineContext[Job].let { parent ->
        if (supervisedApplication) SupervisorJob(parent) else Job(parent)
    }

    val applicationScope = CoroutineScope(testScope.backgroundScope.coroutineContext + applicationJob)

    private val sessionJob = SupervisorJob(testScope.backgroundScope.coroutineContext[Job])

    val sessionScope = CoroutineScope(testScope.backgroundScope.coroutineContext + sessionJob)

    var subjectLoads = 0

    var mediaListFor: (episodeId: Int) -> List<Media> = { episodeId ->
        TestMediaList.map { it.copy(episodeRange = EpisodeRange.single(EpisodeSort(episodeId))) }
    }

    val queried = mutableListOf<Int>()

    val released = mutableListOf<Int>()

    val savedPreferences = mutableListOf<MediaPreference>()

    val creationStarted = mutableListOf<Int>()

    val created = mutableListOf<Creation>()

    val log = mutableListOf<String>()

    var subjectFailure: Throwable? = null

    var preferenceFailure: Int? = null

    var creationFailure: Int? = null

    var prepareGate: CompletableDeferred<Unit>? = null

    var createGate: CompletableDeferred<Unit>? = null

    var listCreated = true

    var subject: SubjectCollectionInfo = requestTestSubject()

    private var currentEpisodeId = 0

    val storage = DownloadTestStorage(mediaSourceId = STORAGE_ID)
    val downloadManager = MediaDownloadManager(listOf(storage), applicationScope)
    val factory = DownloadRequestSessionFactory(
        Subjects(), Preferences(), Sources(), Selectors(), downloadManager, AddDownload(),
    )

    data class Creation(
        val subject: SubjectInfo,
        val episode: EpisodeInfo,
        val media: Media,
        val metadata: MediaCacheMetadata,
    ) {
        val episodeId: Int get() = episode.episodeId
    }

    fun create(episodeIds: List<Int>, parentScope: CoroutineScope = sessionScope): DownloadRequestSession =
        factory.create(subject.subjectId, episodeIds, parentScope)

    fun recordStates(session: DownloadRequestSession): List<DownloadRequestState> {
        val states = mutableListOf<DownloadRequestState>()
        testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
            session.state.collect { states += it }
        }
        return states
    }

    suspend fun close() {
        sessionJob.cancelAndJoin()
        applicationJob.cancelAndJoin()
    }

    private inner class Subjects : SubjectCollectionRepository() {
        override fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo> = flow {
            subjectLoads++
            prepareGate?.await()
            subjectFailure?.let { throw it }
            check(subjectId == subject.subjectId) { "Unknown subject $subjectId" }
            emit(subject)
        }

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> =
            throw UnsupportedOperationException()

        override fun subjectCollectionsPager(
            query: CollectionsFilterQuery,
            pagingConfig: PagingConfig,
        ): Flow<PagingData<SubjectCollectionInfo>> = throw UnsupportedOperationException()

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

        override suspend fun invalidateCache(subjectIds: List<Int>) = throw UnsupportedOperationException()

        override suspend fun invalidateAllCaches() = throw UnsupportedOperationException()
    }

    private inner class Preferences : EpisodePreferencesRepository {
        override fun mediaPreferenceFlow(subjectId: Int): Flow<MediaPreference> = flowOf(MediaPreference.Empty)

        override suspend fun setMediaPreference(subjectId: Int, mediaPreference: MediaPreference) {
            check(currentEpisodeId != preferenceFailure) { "save failed" }
            savedPreferences += mediaPreference
            log += "save:$currentEpisodeId"
        }

        override suspend fun setPreferredWebMediaSource(subjectId: Int, webSourceId: String) =
            throw UnsupportedOperationException()

        override fun getPreferredWebMediaSource(subjectId: Int): Flow<String?> = throw UnsupportedOperationException()

        override suspend fun removePreferredWebMediaSource(subjectId: Int) = throw UnsupportedOperationException()
    }

    private inner class Sources : MediaSourceManager {
        override val allInstances: Flow<List<MediaSourceInstance>> = flowOf(emptyList())
        override val allFactories: List<MediaSourceFactory> = emptyList()
        override val allFactoryIds: List<FactoryId> = emptyList()
        override val mediaFetcher: Flow<MediaFetcher> = flowOf(Fetcher())
        override val webVideoMatcherLoader = MediaSourceWebVideoMatcherLoader(flowOf(emptyList<MediaSource>()))

        override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> = flowOf(null)

        override suspend fun addInstance(
            instanceId: String,
            mediaSourceId: String,
            factoryId: FactoryId,
            config: MediaSourceConfig,
        ) = throw UnsupportedOperationException()

        override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> =
            throw UnsupportedOperationException()

        override suspend fun partiallyReorderInstances(instanceIds: List<String>) =
            throw UnsupportedOperationException()

        override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean =
            throw UnsupportedOperationException()

        override suspend fun setEnabled(instanceId: String, enabled: Boolean) = throw UnsupportedOperationException()

        override suspend fun removeInstance(instanceId: String) = throw UnsupportedOperationException()

        override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> = flowOf(MediaSelectorSourceTiers.Empty)
    }

    private inner class Fetcher : MediaFetcher {
        override fun newSession(requestLazy: Flow<MediaFetchRequest>, flowContext: CoroutineContext): MediaFetchSession =
            FetchSession(requestLazy)
    }

    private inner class FetchSession(override val request: Flow<MediaFetchRequest>) : MediaFetchSession {
        override val mediaSourceResults: List<MediaSourceFetchResult> = emptyList()

        override val cumulativeResults: Flow<List<Media>> = flow {
            val episodeId = request.first().episodeId.toInt()
            queried += episodeId
            try {
                emit(mediaListFor(episodeId))
                awaitCancellation()
            } finally {
                released += episodeId
            }
        }.shareIn(testScope.backgroundScope, SharingStarted.WhileSubscribed(), replay = 1)

        override val hasCompleted: Flow<CompletedConditions> = flowOf(CompletedConditions.AllCompleted)

        override fun setFetchRequest(request: MediaFetchRequest) = Unit
    }

    private inner class Selectors : MediaSelectorFactory {
        override fun create(
            subjectId: Int,
            episodeId: Int,
            mediaList: Flow<List<Media>>,
            flowCoroutineContext: CoroutineContext,
            fetchRequest: Flow<MediaFetchRequest>?,
        ): MediaSelector {
            currentEpisodeId = episodeId
            return DefaultMediaSelector(
                mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
                mediaListNotCached = mediaList,
                savedUserPreference = flowOf(MediaPreference.Empty),
                savedDefaultPreference = flowOf(MediaPreference.Empty),
                mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
                flowCoroutineContext = EmptyCoroutineContext,
                enableCaching = false,
            )
        }
    }

    private inner class AddDownload : AddDownloadUseCase {
        override suspend fun invoke(
            subject: SubjectInfo,
            episode: EpisodeInfo,
            media: Media,
            metadata: MediaCacheMetadata,
        ): MediaCache {
            val episodeId = episode.episodeId
            creationStarted += episodeId
            createGate?.await()
            check(episodeId != creationFailure) { "create failed" }
            val cache = requestTestCache(media, subject.subjectId, episodeId)
            created += Creation(subject, episode, media, metadata)
            log += "create:$episodeId"
            if (listCreated) storage.listFlow.value += cache
            return cache
        }
    }

    companion object {
        const val STORAGE_ID = "request-test-storage"
    }
}
