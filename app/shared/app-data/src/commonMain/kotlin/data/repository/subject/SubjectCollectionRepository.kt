package com.wynime.app.data.repository.subject

import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.subject.RatingCounts
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.data.models.subject.SubjectAiringInfo
import com.wynime.app.data.models.subject.SubjectCollectionCounts
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.app.data.models.subject.SubjectTmdbArt
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.data.models.subject.TmdbImage
import com.wynime.app.data.network.EpisodeService
import com.wynime.app.data.network.SubjectService
import com.wynime.app.data.network.SubjectCollectionPage
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionDao
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionEntity
import com.wynime.app.data.persistent.database.dao.SubjectCollectionDao
import com.wynime.app.data.persistent.database.dao.SubjectCollectionEntity
import com.wynime.app.data.persistent.database.dao.SubjectRelations
import com.wynime.app.data.persistent.database.dao.SubjectRelationsDao
import com.wynime.app.data.persistent.database.dao.deleteAll
import com.wynime.app.data.persistent.database.dao.filterMostRecentUpdated
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.episode.toEpisodeCollectionInfo
import com.wynime.app.data.repository.shouldRetry
import com.wynime.app.domain.search.SubjectType
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.domain.session.checkAccessBangumiApiNow
import com.wynime.app.domain.session.restartOnNewLogin
import com.wynime.models.AnimeRecurrenceDto
import com.wynime.models.CollectionTypeDto
import com.wynime.models.EpisodeCollectionDto
import com.wynime.models.EpisodeCollectionTypeDto
import com.wynime.models.EpisodeTypeDto
import com.wynime.models.FavouriteDto
import com.wynime.models.SelfRatingInfoDto
import com.wynime.models.SubjectCollectionDto
import com.wynime.models.SubjectRelationsDto
import com.wynime.models.TagDto
import com.wynime.models.TmdbImageDto
import com.wynime.models.TmdbSubjectArtDto
import com.wynime.models.UpdateSubjectCollectionRequestDto
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.bangumi.processing.toSubjectCollectionType
import com.wynime.utils.coroutines.combine
import com.wynime.utils.coroutines.flows.flowOfEmptyList
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.currentTimeMillis
import com.wynime.utils.serialization.BigNum
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

abstract class SubjectCollectionRepository(
    defaultDispatcher: CoroutineContext = Dispatchers.Default
) : Repository(defaultDispatcher) {

    abstract fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?>

    abstract fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo>

    abstract fun subjectCollectionsPager(
        query: CollectionsFilterQuery = CollectionsFilterQuery.Empty,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 30,
            prefetchDistance = 30,
        ),
    ): Flow<PagingData<SubjectCollectionInfo>>

    abstract fun cachedValidSubjectIds(): Flow<List<Int>>

    abstract suspend fun updateRecentlyUpdatedSubjectCollections(
        limit: Int,
        type: UnifiedCollectionType?,
        offset: Int = 0,
    )

    abstract fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>? = null,
    ): Flow<List<SubjectCollectionInfo>>

    abstract suspend fun updateRating(
        subjectId: Int,
        score: Int? = null,
        comment: String? = null,
        tags: List<String>? = null,
        isPrivate: Boolean? = null,
    )

    abstract suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    )

    abstract fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?>

    abstract fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?>

    abstract suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>>

    abstract suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>>

    abstract suspend fun performBangumiFullSync()

    abstract suspend fun getBangumiFullSyncState(): BangumiSyncState?

    open suspend fun getBangumiFullSyncSummary(): BangumiFullSyncSummary? = null

    abstract suspend fun invalidateCache(subjectIds: List<Int>)

    abstract suspend fun invalidateAllCaches()

    private val _collectionsInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val collectionsInvalidated: SharedFlow<Unit> = _collectionsInvalidated.asSharedFlow()

    @TestOnly
    val collectionsInvalidatedSubscriptionCount: StateFlow<Int>
        get() = _collectionsInvalidated.subscriptionCount

    protected fun notifyCollectionsInvalidated() {
        _collectionsInvalidated.tryEmit(Unit)
    }
}

data class BangumiFullSyncSummary(
    val savedSubjectCount: Int,
    val expectedSubjectCount: Int?,
    val episodeCount: Int,
    val watchedEpisodeCount: Int,
    val episodeSnapshotCount: Int,
    val failedSubjectIds: List<Int>,
    val elapsedMillis: Long,
)

class SubjectCollectionRepositoryImpl(
    private val subjectService: SubjectService,
    private val subjectCollectionDao: SubjectCollectionDao,
    private val subjectRelationsDao: SubjectRelationsDao,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val episodeService: EpisodeService,
    private val episodeCollectionDao: EpisodeCollectionDao,
    private val sessionManager: SessionStateProvider,
    private val nsfwModeSettingsFlow: Flow<NsfwMode>,
    private val getCurrentDate: () -> PackedDate = { PackedDate.now() },
    private val getEpisodeTypeFiltersUseCase: GetEpisodeTypeFiltersUseCase,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
    private val cacheExpiry: Duration = 1.hours,
    private val trackingMetadataRepository: BangumiTrackingMetadataRepository? = null,
    private val trackingSyncEnqueuer: BangumiTrackingSyncEnqueuer? = null,
    private val trackingSyncSettingsStore: BangumiTrackingSyncSettingsStore? = null,
    private val syncCoordinator: BangumiSyncCoordinator = BangumiSyncCoordinator(),
) : SubjectCollectionRepository(defaultDispatcher) {
    private val bangumiFullSyncMutex = Mutex()
    private val fullSyncScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val fullSyncJobMutex = Mutex()
    private var runningFullSync: Deferred<Unit>? = null
    private val bangumiFullSyncState = MutableStateFlow<BangumiSyncState?>(null)
    private val bangumiFullSyncSummary = MutableStateFlow<BangumiFullSyncSummary?>(null)

    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?> {
        return combine(
            subjectCollectionDao.countCollected(UnifiedCollectionType.WISH),
            subjectCollectionDao.countCollected(UnifiedCollectionType.DOING),
            subjectCollectionDao.countCollected(UnifiedCollectionType.DONE),
            subjectCollectionDao.countCollected(UnifiedCollectionType.ON_HOLD),
            subjectCollectionDao.countCollected(UnifiedCollectionType.DROPPED),
        ) { wish, doing, done, onHold, dropped ->
            SubjectCollectionCounts(
                wish = wish,
                doing = doing,
                done = done,
                onHold = onHold,
                dropped = dropped,
                total = wish + doing + done + onHold + dropped,
            )
        }.flowOn(defaultDispatcher)
    }

    private fun SubjectCollectionEntity.isExpired(): Boolean {
        return (currentTimeMillis() - lastFetched).milliseconds > cacheExpiry
    }

    override fun subjectCollectionFlow(
        subjectId: Int
    ): Flow<SubjectCollectionInfo> = getEpisodeTypeFiltersUseCase().flatMapLatest { epTypes ->
        subjectCollectionDao.findById(subjectId)
            .restartOnNewLogin(sessionManager)
            .transform { existing ->
                if (existing != null) {

                    emit(existing)
                }

                if (existing == null || existing.isExpired()) {
                    refetchSubjectCollection(subjectId)

                }
            }
            .filterNotNull()

            .combine(
                episodeCollectionDao
                    .filterBySubjectId(subjectId, epTypes)
                    .map { list -> list.map { it.toEpisodeCollectionInfo() } }
                    .distinctUntilChanged(),
                nsfwModeSettingsFlow,
            ) { entity, episodes, nsfwModeSettings ->
                entity.toSubjectCollectionInfo(
                    episodes = episodes,
                    currentDate = getCurrentDate(),
                    nsfwModeSettings = nsfwModeSettings,
                )
            }
    }.flowOn(defaultDispatcher)

    private suspend fun refetchSubjectCollection(subjectId: Int): SubjectCollectionDto? {
        val subject = subjectService.getSubjectCollection(subjectId) ?: return null
        val lastFetched = currentTimeMillis()
        val subjectEntity = subject.toEntity(lastFetched = lastFetched)
        val tombstone = trackingMetadataRepository?.find(subjectId)
        if (tombstone?.localDeletedAt != null && subjectEntity.lastUpdated <= tombstone.localDeletedAt) {
            val protectedEntity = subjectCollectionDao.findById(subjectId).first()
                ?.copy(
                    lastUpdated = maxOf(tombstone.localDeletedAt, subjectEntity.lastUpdated),
                    lastFetched = lastFetched,
                )
                ?: subjectEntity.copy(
                    lastUpdated = tombstone.localDeletedAt,
                )
            subjectCollectionDao.upsert(protectedEntity)
            saveEpisodeEntities(subjectId, subject.episodes, lastFetched)
            return subject
        }
        if (tombstone?.localDeletedAt != null && subjectEntity.lastUpdated > tombstone.localDeletedAt) {
            trackingMetadataRepository.clearTombstone(subjectId, subjectEntity.lastUpdated)
        }
        val episodeEntities = subject.episodes.map {
            it.toEntity1(subjectId, lastFetched = lastFetched)
        }
        subjectCollectionDao.upsert(subjectEntity)

        val oldIds = episodeCollectionDao.listIdBySubjectId(subjectId).first().toMutableList()
        episodeCollectionDao.upsert(episodeEntities)
        for (newEntity in episodeEntities) {
            oldIds.remove(newEntity.episodeId)
        }
        if (oldIds.isNotEmpty()) {
            episodeCollectionDao.deleteAllByEpisodeIds(subjectId, oldIds)
        }
        return subject
    }

    private suspend fun saveEpisodeEntities(
        subjectId: Int,
        episodes: List<EpisodeCollectionDto>,
        lastFetched: Long,
    ) {
        val episodeEntities = episodes.map { it.toEntity1(subjectId, lastFetched = lastFetched) }
        val oldIds = episodeCollectionDao.listIdBySubjectId(subjectId).first().toMutableList()
        episodeCollectionDao.upsert(episodeEntities)
        for (newEntity in episodeEntities) oldIds.remove(newEntity.episodeId)
        if (oldIds.isNotEmpty()) episodeCollectionDao.deleteAllByEpisodeIds(subjectId, oldIds)
    }

    override fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>?,
    ): Flow<List<SubjectCollectionInfo>> = subjectCollectionDao.filterMostRecentUpdated(types, limit)
        .restartOnNewLogin(sessionManager)
        .combine(nsfwModeSettingsFlow) { list, nsfwModeSettings ->
            list to nsfwModeSettings
        }
        .flatMapLatest { (list, nsfwModeSettings) ->
            if (list.isEmpty()) {
                return@flatMapLatest flowOfEmptyList()
            }
            combine(
                list.map { entity ->
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(entity.subjectId).map { episodes ->
                        entity.toSubjectCollectionInfo(
                            episodes = episodes,
                            currentDate = getCurrentDate(),
                            nsfwModeSettings = nsfwModeSettings,
                        )
                    }
                },
            ) {
                it.toList()
            }
        }
        .flowOn(defaultDispatcher)

    override fun subjectCollectionsPager(
        query: CollectionsFilterQuery,
        pagingConfig: PagingConfig,
    ): Flow<PagingData<SubjectCollectionInfo>> =
        combine(getEpisodeTypeFiltersUseCase(), nsfwModeSettingsFlow) { epTypes, nsfwModeSettings ->
            epTypes to nsfwModeSettings
        }.restartOnNewLogin(sessionManager).flatMapLatest { (epTypes, nsfwModeSettings) ->
            Pager(
                config = pagingConfig,
                initialKey = 0,
                remoteMediator = SubjectCollectionRemoteMediator(query),
                pagingSourceFactory = {
                    subjectCollectionDao.filterByCollectionTypePaging(
                        query.type,
                        includeNsfw = nsfwModeSettings != NsfwMode.HIDE,
                    )
                },
            ).flow.map { data ->
                data.map { (entity, episodesOfAnyType) ->
                    val date = getCurrentDate()
                    entity.toSubjectCollectionInfo(
                        episodes = episodesOfAnyType
                            .asSequence()
                            .let { sequence ->
                                sequence.filter { it.episodeType in epTypes }
                            }
                            .map { it.toEpisodeCollectionInfo() }
                            .toList(),
                        currentDate = date,
                        nsfwModeSettings = nsfwModeSettings,
                    )
                }
            }
        }.flowOn(defaultDispatcher)

    override fun cachedValidSubjectIds(): Flow<List<Int>> {
        return subjectCollectionDao.subjectIdsWithValidEpisodeCollection().flowOn(defaultDispatcher)
    }

    private val updateRecentlyUpdatedSubjectCollectionsMutex = Mutex()
    override suspend fun updateRecentlyUpdatedSubjectCollections(
        limit: Int,
        type: UnifiedCollectionType?,
        offset: Int
    ) {
        try {
            withContext(defaultDispatcher) {

                updateRecentlyUpdatedSubjectCollectionsMutex.withLock {
                    fetchAndSaveSubjectCollectionsWithEpisodes(type, limit, offset)
                }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private suspend inline fun fetchAndSaveSubjectCollectionsWithEpisodes(
        type: UnifiedCollectionType?,
        limit: Int,
        offset: Int,
        onFetched: (page: SubjectCollectionPage) -> Unit = {},
    ) {
        require(type != UnifiedCollectionType.NOT_COLLECTED) { "type must not be NOT_COLLECTED" }
        require(limit > 0) { "limit must be positive" }

        val page = subjectService.getSubjectCollectionsPage(
            type = type?.toSubjectCollectionType(),
            offset = offset,
            limit = limit,
        )
        logger.debug {
            "Bangumi collection page type=$type offset=$offset raw=${page.sourceItemCount} " +
                "hydrated=${page.items.size} total=${page.total} omitted=${page.omittedSubjectIds.size} " +
                "subjectWorkers=3"
        }
        if (page.omittedSubjectIds.isNotEmpty()) {
            throw RepositoryRequestError(
                "收藏頁資料不完整，缺少 subject：${page.omittedSubjectIds.joinToString()}",
            )
        }
        page.total?.let { total ->
            if (total == 0 && page.sourceItemCount != 0) {
                throw RepositoryRequestError(
                    "收藏頁 total=0 但回傳了 ${page.sourceItemCount} 筆資料：offset=$offset",
                )
            }
            if (offset < total && page.items.isEmpty()) {
                throw RepositoryRequestError("收藏頁 offset=$offset 為空，但 total=$total")
            }
            if (offset + page.sourceItemCount < total && page.sourceItemCount < limit) {
                throw RepositoryRequestError(
                    "收藏頁提前結束：offset=$offset，取得 ${page.items.size}，total=$total",
                )
            }
        }

        val lastFetched = currentTimeMillis()
        saveSubjectCollectionsWithEpisodes(page.items, lastFetched)
        onFetched(page)
    }

    private suspend fun saveSubjectCollectionsWithEpisodes(
        items: List<SubjectCollectionDto>,
        lastFetched: Long,
    ) {
        val entities = ArrayList<SubjectCollectionEntity>(items.size)
        for (item in items) {
            val entity = item.toEntity(lastFetched = lastFetched)
            val tombstone = trackingMetadataRepository?.find(entity.subjectId)
            if (tombstone?.localDeletedAt != null && entity.lastUpdated <= tombstone.localDeletedAt) {

                entities += entity.copy(
                    collectionType = subjectCollectionDao.findById(entity.subjectId).first()?.collectionType
                        ?: entity.collectionType,
                    lastUpdated = tombstone.localDeletedAt,
                )
            } else {
                if (tombstone?.localDeletedAt != null) {
                    trackingMetadataRepository.clearTombstone(entity.subjectId, entity.lastUpdated)
                }
                entities += entity
            }
        }
        subjectCollectionDao.upsert(entities)

        episodeCollectionDao.upsert(
            items
                .flatMap { it.episodes }
                .map { episode ->
                    episode.toEntity1(
                        subjectId = episode.subjectId.toInt(),
                        lastFetched = lastFetched,
                    )
                },
        )

        items.forEach { item ->
            val subjectId = item.id.toInt()
            val newEpisodeIds = item.episodes.mapTo(HashSet()) { it.episodeId.toInt() }
            val oldEpisodeIds = episodeCollectionDao.listIdBySubjectId(subjectId).first()
            val staleEpisodeIds = oldEpisodeIds.filterNot(newEpisodeIds::contains)
            if (staleEpisodeIds.isNotEmpty()) {
                episodeCollectionDao.deleteAllByEpisodeIds(subjectId, staleEpisodeIds)
            }
        }
    }

    override suspend fun updateRating(
        subjectId: Int,
        score: Int?,
        comment: String?,
        tags: List<String>?,
        isPrivate: Boolean?,
    ) {
        withContext(defaultDispatcher) {
            subjectService.patchSubjectCollection(
                subjectId,
                UpdateSubjectCollectionRequestDto(
                    selfRating = SelfRatingInfoDto(
                        score = score ?: 0,
                        comment = comment,
                        tags = tags.orEmpty(),
                        isPrivate = isPrivate ?: false,
                    ),
                ),
            )

            subjectCollectionDao.updateRating(
                subjectId,
                score,
                comment,
                tags,
                isPrivate,
            )
        }
    }

    private inner class SubjectCollectionRemoteMediator<T : Any>(
        private val query: CollectionsFilterQuery,
    ) : RemoteMediator<Int, T>() {
        private var refreshOriginalIds: Set<Int>? = null
        private val refreshSeenIds = mutableSetOf<Int>()

        override suspend fun initialize(): InitializeAction = withContext(defaultDispatcher) {
            val lastUpdated = subjectCollectionDao.lastFetched(query.type)
            if ((currentTimeMillis() - lastUpdated).milliseconds > cacheExpiry) {
                InitializeAction.LAUNCH_INITIAL_REFRESH
            } else {
                InitializeAction.SKIP_INITIAL_REFRESH
            }
        }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult = try {
            syncCoordinator.withExclusive(BangumiSyncOperation.COLLECTION_PAGE_REFRESH) {
                withContext(defaultDispatcher) {
                val (offset, limit) = calculateIndexBasedLoadInfo(loadType, state)
                    ?: return@withContext MediatorResult.Success(endOfPaginationReached = true)
                logger.debug { "${loadType}, Loading $offset, limit=$limit" }

                if (loadType == LoadType.REFRESH) {
                    refreshOriginalIds = query.type?.let { collectionType ->
                        subjectCollectionDao.listIdsByCollectionType(collectionType)
                    }?.toSet()
                    refreshSeenIds.clear()
                }
                var endOfPaginationReached = false
                fetchAndSaveSubjectCollectionsWithEpisodes(
                    type = query.type,
                    limit = limit,
                    offset = offset,
                    onFetched = { page ->
                        if (loadType == LoadType.REFRESH) {
                            refreshSeenIds += page.items.map { it.id.toInt() }
                        }
                        endOfPaginationReached = page.total?.let { total ->
                            offset + page.sourceItemCount >= total
                        } ?: (page.sourceItemCount < limit)

                        if (endOfPaginationReached) {
                            val staleIds = refreshOriginalIds.orEmpty().filterNot(refreshSeenIds::contains)
                            if (staleIds.isNotEmpty()) subjectCollectionDao.deleteByIds(staleIds)
                            refreshOriginalIds = null
                            refreshSeenIds.clear()
                        }
                    },
                )

                MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
                }
            }
        } catch (e: Exception) {
            MediatorResult.Error(RepositoryException.wrapOrThrowCancellation(e))
        }
    }

    override suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    ) {
        return withContext(defaultDispatcher) {
            val now = currentTimeMillis()
            if (type == null || type == UnifiedCollectionType.NOT_COLLECTED) {

                val metadata = trackingMetadataRepository
                    ?: throw RepositoryRequestError("取消收藏的同步狀態儲存未設定")
                metadata.markLocalDeletion(subjectId, now)
            } else {
                val updated = subjectCollectionDao.updateType(
                    subjectId = subjectId,
                    collectionType = type,
                    lastUpdated = now,
                    lastFetched = now,
                )
                if (updated == 0) {

                    refetchSubjectCollection(subjectId)
                    subjectCollectionDao.updateType(
                        subjectId = subjectId,
                        collectionType = type,
                        lastUpdated = now,
                        lastFetched = now,
                    )
                }
                trackingMetadataRepository?.markLocalChange(subjectId, type, now)
            }
            if (trackingSyncSettingsStore?.flow?.first()?.autoSyncTracking == true) {
                trackingSyncEnqueuer?.enqueueLocalChange(subjectId)
            }
        }
    }

    override fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?> {
        return subjectCollectionDao.findById(subjectId).map { it?.collectionType }
    }

    override fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?> {
        return subjectCollectionDao.findById(subjectId).map { entity ->
            entity?.run {
                OfflineSubjectDisplayInfo(
                    subjectId = this.subjectId,
                    displayName = nameCn.ifEmpty { name },
                    imageLarge = imageLarge,
                    imageThumb = imageThumb.ifEmpty { imageLarge },
                    totalEpisodes = totalEpisodes,
                )
            }
        }
    }

    override suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>> {
        return subjectCollectionDao.subjectIdsByCollectionType(types).flowOn(defaultDispatcher)
    }

    override suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>> {
        return subjectCollectionDao.subjectNamesCnByCollectionType(types).flowOn(defaultDispatcher)
    }

    override suspend fun performBangumiFullSync() {
        val deferred = fullSyncJobMutex.withLock {
            runningFullSync ?: fullSyncScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                performBangumiFullSyncInternal()
            }.also { runningFullSync = it }
        }
        try {
            deferred.await()
        } finally {
            fullSyncJobMutex.withLock {
                if (runningFullSync === deferred && deferred.isCompleted) {
                    runningFullSync = null
                }
            }
        }
    }

    private suspend fun performBangumiFullSyncInternal() {
        var savedCount = 0
        try {
            syncCoordinator.withExclusive(BangumiSyncOperation.COLLECTION_REFRESH) {
                withContext(defaultDispatcher) {
                    bangumiFullSyncMutex.withLock {
                    bangumiFullSyncSummary.value = null
                    sessionManager.checkAccessBangumiApiNow()
                    bangumiFullSyncState.value = BangumiSyncState.Preparing
                    val syncStartedAt = currentTimeMillis()
                    val pageSize = 100
                    val remoteCollections = ArrayList<SubjectCollectionDto>()
                    val collectionTotals = mutableMapOf<UnifiedCollectionType, Int?>()
                    val failedSubjectIds = mutableListOf<Int>()
                    val failureMessages = mutableListOf<String>()
                    val seenSourceSubjectIds = mutableSetOf<Int>()
                    var episodeCount = 0
                    var watchedEpisodeCount = 0
                    var episodeSnapshotCount = 0

                    for (type in FULL_SYNC_COLLECTION_TYPES) {
                        var offset = 0
                        while (true) {
                            val page = subjectService.getSubjectCollectionsPage(
                                type = type.toSubjectCollectionType(),
                                offset = offset,
                                limit = pageSize,
                                onItemHydrated = { current, total ->
                                    syncCoordinator.report(
                                        operation = BangumiSyncOperation.COLLECTION_REFRESH,
                                        phase = BangumiSyncPhase.FETCHING_EPISODES,
                                        current = current,
                                        total = total,
                                    )
                                },
                            )
                            if (page.omittedSubjectIds.isNotEmpty()) {
                                failedSubjectIds += page.omittedSubjectIds
                                failureMessages += "收藏頁缺少 subject：${page.omittedSubjectIds.joinToString()}"
                            }
                            logger.info {
                                "Bangumi full sync type=$type offset=$offset raw=${page.sourceItemCount} " +
                                    "hydrated=${page.items.size} total=${page.total} " +
                                    "omitted=${page.omittedSubjectIds.size} subjectWorkers=3"
                            }
                            val pageSubjectIds = page.items.map { it.id.toInt() } + page.omittedSubjectIds
                            if (page.sourceItemCount != pageSubjectIds.size ||
                                pageSubjectIds.size != pageSubjectIds.toSet().size ||
                                pageSubjectIds.any { !seenSourceSubjectIds.add(it) }
                            ) {
                                throw RepositoryRequestError("收藏頁 subject ID 重複或數量不一致：offset=$offset")
                            }
                            collectionTotals.putIfAbsent(type, page.total)
                            remoteCollections += page.items
                            val total = collectionTotals.values.sumOf { it ?: 0 }.takeIf {
                                collectionTotals.values.all { it != null }
                            }
                            bangumiFullSyncState.value = BangumiSyncState.FetchingSubjects(
                                fetchedCount = remoteCollections.size,
                                totalCount = total,
                            )
                            syncCoordinator.report(
                                operation = BangumiSyncOperation.COLLECTION_REFRESH,
                                phase = BangumiSyncPhase.FETCHING_COLLECTIONS,
                                current = remoteCollections.size,
                                total = total,
                            )
                            val reachedEnd = page.total?.let { total ->
                                if (total == 0 && page.sourceItemCount != 0) {
                                    throw RepositoryRequestError(
                                        "收藏頁 total=0 但回傳了 ${page.sourceItemCount} 筆資料：offset=$offset",
                                    )
                                }
                                if (offset < total && page.sourceItemCount == 0) {
                                    throw RepositoryRequestError(
                                        "收藏頁 offset=$offset 為空，但 total=$total",
                                    )
                                }
                                offset + page.sourceItemCount >= total
                            } ?: (page.sourceItemCount < pageSize)
                            if (reachedEnd) break
                            if (page.sourceItemCount <= 0) {
                                throw RepositoryRequestError("收藏頁 offset=$offset 未前進")
                            }
                            offset += page.sourceItemCount
                        }
                    }

                    bangumiFullSyncState.value = BangumiSyncState.FetchingEpisodes(
                        fetchedCount = remoteCollections.size,
                        totalCount = remoteCollections.size,
                    )
                    syncCoordinator.report(
                        operation = BangumiSyncOperation.COLLECTION_REFRESH,
                        phase = BangumiSyncPhase.FETCHING_EPISODES,
                        current = remoteCollections.size,
                        total = remoteCollections.size,
                    )

                    val existing = subjectCollectionDao.listAll()
                        .associateBy { it.subjectId }
                    val remoteById = remoteCollections.distinctBy { it.id }.associateBy { it.id.toInt() }
                    val protectedLocalIds = existing.values
                        .asSequence()
                        .filter { it.subjectId !in remoteById && it.lastUpdated > syncStartedAt }
                        .map { it.subjectId }
                        .toSet()
                    val tombstoneIds = existing.values.asSequence()
                        .filter { it.collectionType == UnifiedCollectionType.NOT_COLLECTED }
                        .map { it.subjectId }
                        .toSet()
                    val staleIds = existing.keys - remoteById.keys - protectedLocalIds - tombstoneIds

                    bangumiFullSyncState.value = BangumiSyncState.Inserting(0, remoteById.size)
                    val lastFetched = currentTimeMillis()
                    remoteById.forEach { (subjectId, remote) ->
                        val remoteEntity = remote.toEntity(lastFetched)
                        val local = existing[subjectId]
                        episodeCount += remote.episodes.size
                        watchedEpisodeCount += remote.episodes.count { it.collectionType == EpisodeCollectionTypeDto.DONE }
                        try {
                            val tombstone = trackingMetadataRepository?.find(subjectId)
                            if (tombstone?.localDeletedAt != null &&
                                remoteEntity.lastUpdated <= tombstone.localDeletedAt
                            ) {
                                subjectCollectionDao.updateType(
                                    subjectId = subjectId,
                                    collectionType = local?.collectionType ?: remoteEntity.collectionType,
                                    lastUpdated = tombstone.localDeletedAt,
                                    lastFetched = lastFetched,
                                )
                            } else {
                                if (tombstone?.localDeletedAt != null) {
                                    trackingMetadataRepository.clearTombstone(subjectId, remoteEntity.lastUpdated)
                                }

                                if (local == null ||
                                    remoteEntity.lastUpdated <= 0L ||
                                    local.lastUpdated <= remoteEntity.lastUpdated
                                ) {
                                    saveSubjectCollectionsWithEpisodes(listOf(remote), lastFetched)
                                    savedCount++
                                    episodeSnapshotCount += remote.episodes.size
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failedSubjectIds += subjectId
                            e.message?.let(failureMessages::add)
                        }
                        bangumiFullSyncState.value = BangumiSyncState.Inserting(savedCount, remoteById.size)
                        syncCoordinator.report(
                            operation = BangumiSyncOperation.COLLECTION_REFRESH,
                            phase = BangumiSyncPhase.APPLYING_LOCAL,
                            current = savedCount,
                            total = remoteById.size,
                            failedCount = failedSubjectIds.size,
                        )
                    }

                    if (failedSubjectIds.isNotEmpty()) {
                        val detail = "${failedSubjectIds.size} 個條目同步失敗"
                        bangumiFullSyncState.value = BangumiSyncState.Finished(
                            savedCount,
                            null,
                            detail + failureMessages.distinct().joinToString(prefix = "："),
                        )
                        syncCoordinator.partialFailure(
                            operation = BangumiSyncOperation.COLLECTION_REFRESH,
                            failedCount = failedSubjectIds.size,
                            detail = detail,
                        )
                        bangumiFullSyncSummary.value = BangumiFullSyncSummary(
                            savedSubjectCount = savedCount,
                            expectedSubjectCount = collectionTotals.values
                                .takeIf { it.all { total -> total != null } }
                                ?.sumOf { it ?: 0 },
                            episodeCount = episodeCount,
                            watchedEpisodeCount = watchedEpisodeCount,
                            episodeSnapshotCount = episodeSnapshotCount,
                            failedSubjectIds = failedSubjectIds.distinct().sorted(),
                            elapsedMillis = currentTimeMillis() - syncStartedAt,
                        )
                        notifyCollectionsInvalidated()
                        throw RepositoryRequestError(detail)
                    }

                    if (staleIds.isNotEmpty()) {
                        subjectCollectionDao.deleteByIds(staleIds.toList())
                    }

                    bangumiFullSyncState.value = BangumiSyncState.Finishing(savedCount, remoteById.size)
                    syncCoordinator.report(
                        operation = BangumiSyncOperation.COLLECTION_REFRESH,
                        phase = BangumiSyncPhase.RELOADING,
                        current = savedCount,
                        total = remoteById.size,
                    )
                    bangumiFullSyncState.value = BangumiSyncState.Finished(savedCount, null)
                    bangumiFullSyncSummary.value = BangumiFullSyncSummary(
                        savedSubjectCount = savedCount,
                        expectedSubjectCount = collectionTotals.values
                            .takeIf { it.all { total -> total != null } }
                            ?.sumOf { it ?: 0 },
                        episodeCount = episodeCount,
                        watchedEpisodeCount = watchedEpisodeCount,
                        episodeSnapshotCount = episodeSnapshotCount,
                        failedSubjectIds = emptyList(),
                        elapsedMillis = currentTimeMillis() - syncStartedAt,
                    )
                    logger.info {
                        "Bangumi full sync completed subjects=$savedCount " +
                            "episodes=$episodeSnapshotCount watched=$watchedEpisodeCount " +
                            "elapsedMs=${currentTimeMillis() - syncStartedAt} workers=3"
                    }
                    notifyCollectionsInvalidated()
                    }
                }
            }
        } catch (e: Exception) {
            if (bangumiFullSyncState.value !is BangumiSyncState.Finished) {
                bangumiFullSyncState.value = BangumiSyncState.Finished(
                    savedCount,
                    null,
                    e.message ?: e::class.simpleName,
                )
            }
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    override suspend fun getBangumiFullSyncState(): BangumiSyncState? {
        return bangumiFullSyncState.value
    }

    override suspend fun getBangumiFullSyncSummary(): BangumiFullSyncSummary? {
        return bangumiFullSyncSummary.value
    }

    override suspend fun invalidateCache(subjectIds: List<Int>) {
        if (subjectIds.isEmpty()) return
        withContext(defaultDispatcher) {
            coroutineScope {

                val semaphore = Semaphore(INVALIDATE_REFETCH_PARALLELISM)

                val failed = atomic(false)
                subjectIds.distinct().map { subjectId ->
                    async {
                        semaphore.withPermit {
                            if (failed.value) return@withPermit
                            val fetched = try {
                                refetchSubjectCollection(subjectId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {

                                failed.value = true
                                logger.warn(e) { "Failed to refetch subject collection $subjectId after invalidation, keeping the cached row and skipping the remaining refetches" }
                                return@withPermit
                            }
                            if (fetched == null || fetched.collectionType == null) {
                                val tombstone = trackingMetadataRepository?.find(subjectId)
                                if (tombstone?.localDeletedAt != null) {

                                    subjectCollectionDao.updateType(
                                        subjectId = subjectId,
                                        collectionType = subjectCollectionDao.findById(subjectId).first()?.collectionType
                                            ?: UnifiedCollectionType.NOT_COLLECTED,
                                        lastUpdated = tombstone.localDeletedAt,
                                        lastFetched = currentTimeMillis(),
                                    )
                                } else {

                                    subjectCollectionDao.delete(subjectId)
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            subjectCollectionDao.resetAllLastFetched()
        }
        notifyCollectionsInvalidated()
    }

    override suspend fun invalidateAllCaches() {
        withContext(defaultDispatcher) {
            subjectCollectionDao.resetAllLastFetched()
        }
        notifyCollectionsInvalidated()
    }

    private companion object {
        private val logger = logger<SubjectCollectionRepository>()

        private val FULL_SYNC_COLLECTION_TYPES = listOf(
            UnifiedCollectionType.WISH,
            UnifiedCollectionType.DONE,
            UnifiedCollectionType.DOING,
            UnifiedCollectionType.ON_HOLD,
            UnifiedCollectionType.DROPPED,
        )

        private const val INVALIDATE_REFETCH_PARALLELISM = 4
    }
}

data class CollectionsFilterQuery(
    val type: UnifiedCollectionType?,
) {
    companion object {
        val Empty = CollectionsFilterQuery(null)
    }
}

private fun SubjectCollectionEntity.toSubjectInfo(): SubjectInfo {
    return SubjectInfo(
        subjectId = subjectId,
        subjectType = SubjectType.ANIME,
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        imageLarge = imageLarge,
        imageThumb = imageThumb,
        totalEpisodes = totalEpisodes,
        airDate = airDate,
        tags = tags,
        aliases = aliases,
        ratingInfo = ratingInfo,
        collectionStats = collectionStats,
        completeDate = completeDate,
        tmdbArt = tmdbArt,
    )
}

private fun SubjectCollectionEntity.toSubjectCollectionInfo(
    episodes: List<EpisodeCollectionInfo>,
    currentDate: PackedDate,
    nsfwModeSettings: NsfwMode,
): SubjectCollectionInfo {
    val subjectInfo = toSubjectInfo()
    return SubjectCollectionInfo(
        collectionType = collectionType,
        subjectInfo = subjectInfo,
        selfRatingInfo = selfRatingInfo,
        episodes = episodes,
        airingInfo = SubjectAiringInfo.computeFromEpisodeList(
            episodes.map { it.episodeInfo },
            airDate,
            recurrence,
        ),
        progressInfo = SubjectProgressInfo.compute(subjectInfo, episodes, currentDate, recurrence),

        recurrence = recurrence,
        cachedStaffUpdated = cachedStaffUpdated,
        cachedCharactersUpdated = cachedCharactersUpdated,
        lastUpdated = lastUpdated,
        nsfwMode = if (nsfw) nsfwModeSettings else NsfwMode.DISPLAY,
        relations = relations ?: SubjectRelations.Empty,
    )
}

data class LoadInfo(
    val offset: Int,
    val limit: Int,
)

fun <T : Any> calculateIndexBasedLoadInfo(
    loadType: LoadType,
    state: PagingState<Int, T>
): LoadInfo? {
    return when (loadType) {
        LoadType.REFRESH -> {
            LoadInfo(0, state.config.pageSize)
        }

        LoadType.PREPEND -> {
            val firstLoadedPage = state.pages.firstOrNull()
            if (firstLoadedPage != null) {
                if (firstLoadedPage.itemsBefore == 0) {

                    return null
                }
                val offset = firstLoadedPage.itemsBefore - state.config.pageSize
                if (offset >= 0) {
                    LoadInfo(
                        offset,
                        state.config.pageSize,
                    )
                } else {
                    LoadInfo(
                        0,
                        (state.config.pageSize + offset).coerceAtLeast(1),
                    )
                }
            } else {
                LoadInfo(
                    0,
                    state.config.pageSize,
                )
            }
        }

        LoadType.APPEND -> {
            val lastLoadedPage = state.pages.lastOrNull()

            val offset = if (lastLoadedPage != null) {
                lastLoadedPage.itemsBefore + lastLoadedPage.data.size
            } else {
                0
            }
            LoadInfo(
                offset,
                state.config.pageSize,
            )
        }
    }
}

fun SubjectCollectionDto.toEntity(
    lastFetched: Long,
): SubjectCollectionEntity {
    return SubjectCollectionEntity(
        subjectId = id.toInt(),
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        imageLarge = imageLarge,
        imageThumb = imageThumb,
        totalEpisodes = episodes.size,
        airDate = PackedDate.parseFromDate(airDate),
        aliases = aliases,
        tags = tags.map { it.toTag() },
        collectionStats = favorite.toSubjectCollectionStats(),
        ratingInfo = RatingInfo(
            rank = rank ?: 0,
            total = scoreDetails.values.sum(),
            count = RatingCounts(
                s1 = scoreDetails["1"] ?: 0,
                s2 = scoreDetails["2"] ?: 0,
                s3 = scoreDetails["3"] ?: 0,
                s4 = scoreDetails["4"] ?: 0,
                s5 = scoreDetails["5"] ?: 0,
                s6 = scoreDetails["6"] ?: 0,
                s7 = scoreDetails["7"] ?: 0,
                s8 = scoreDetails["8"] ?: 0,
                s9 = scoreDetails["9"] ?: 0,
                s10 = scoreDetails["10"] ?: 0,
            ),
            score = score ?: "0",
        ),
        completeDate = PackedDate.Invalid,
        selfRatingInfo = selfRating.toSelfRatingInfo(),
        collectionType = collectionType.toUnifiedCollectionType(),
        recurrence = airingInfo?.recurrence?.toSubjectRecurrence(),
        relations = relations.toSubjectRelationsEntity(),
        tmdbArt = tmdbArt?.toSubjectTmdbArt(),
        lastUpdated = updatedAt?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
        lastFetched = lastFetched,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )
}

private fun TmdbSubjectArtDto.toSubjectTmdbArt(): SubjectTmdbArt = SubjectTmdbArt(
    backdrops = backdrops.map { it.toTmdbImage() },
    posters = posters.mapValues { it.value.toTmdbImage() },
    logos = logos.mapValues { it.value.toTmdbImage() },
)

private fun TmdbImageDto.toTmdbImage(): TmdbImage = TmdbImage(medium = medium, large = large, vector = vector)

data class OfflineSubjectDisplayInfo(
    val subjectId: Int,
    val displayName: String,
    val imageLarge: String,

    val imageThumb: String,
    val totalEpisodes: Int,
)

fun SubjectRelationsDto.toSubjectRelationsEntity(): SubjectRelations {
    return SubjectRelations(
        seriesMainSubjectIds,
        seriesMainSubjectNames,
        sequelSubjects,
        sequelSubjectNames,
    )
}

fun TagDto.toTag(): Tag = Tag(
    name = name,
    count = count,
)

fun FavouriteDto.toSubjectCollectionStats(): SubjectCollectionStats {
    return SubjectCollectionStats(
        wish = wish,
        doing = doing,
        done = done,
        onHold = onHold,
        dropped = dropped,
    )
}

fun AnimeRecurrenceDto.toSubjectRecurrence(): SubjectRecurrence? {
    return SubjectRecurrence(
        Instant.parse(startTime),
        interval = intervalMillis.milliseconds,
    )
}

fun CollectionTypeDto?.toUnifiedCollectionType(): UnifiedCollectionType {
    return when (this) {
        CollectionTypeDto.WISH -> UnifiedCollectionType.WISH
        CollectionTypeDto.DOING -> UnifiedCollectionType.DOING
        CollectionTypeDto.DONE -> UnifiedCollectionType.DONE
        CollectionTypeDto.ON_HOLD -> UnifiedCollectionType.ON_HOLD
        CollectionTypeDto.DROPPED -> UnifiedCollectionType.DROPPED
        null -> UnifiedCollectionType.NOT_COLLECTED
    }
}

fun EpisodeCollectionDto.toEntity1(
    subjectId: Int,
    lastFetched: Long,
): EpisodeCollectionEntity {
    return EpisodeCollectionEntity(
        subjectId = subjectId,
        episodeId = episodeId.toInt(),
        episodeType = type.toEpisodeType(),
        name = name,
        nameCn = nameCn,
        airDate = airdate?.let { PackedDate.parseFromDate(it) } ?: PackedDate.Invalid,
        comment = 0,
        desc = description,
        sort = EpisodeSort(BigNum(sort), type.toEpisodeType()),
        ep = ep?.let { EpisodeSort(BigNum(it), type.toEpisodeType()) },
        sortNumber = sort.toFloatOrNull() ?: 0f,
        imageMedium = imageMedium,
        imageLarge = imageLarge,
        selfCollectionType = collectionType.toUnifiedCollectionType(),
        lastFetched = lastFetched,
    )
}

fun EpisodeTypeDto.toEpisodeType(): EpisodeType? {
    return when (this) {
        EpisodeTypeDto.MAIN -> EpisodeType.MainStory
        EpisodeTypeDto.SPECIAL -> EpisodeType.SP
        EpisodeTypeDto.OP -> EpisodeType.OP
        EpisodeTypeDto.ED -> EpisodeType.ED
        EpisodeTypeDto.TRAILER -> EpisodeType.PV
        EpisodeTypeDto.MAD -> EpisodeType.MAD
        EpisodeTypeDto.OTHER -> null
    }
}

fun EpisodeCollectionTypeDto?.toUnifiedCollectionType(): UnifiedCollectionType {
    return when (this) {
        null -> UnifiedCollectionType.NOT_COLLECTED
        EpisodeCollectionTypeDto.DONE -> UnifiedCollectionType.DONE
    }
}

fun SelfRatingInfoDto.toSelfRatingInfo(): SelfRatingInfo {
    return SelfRatingInfo(
        score = score, comment = comment, tags = tags, isPrivate = isPrivate,
    )
}

fun UnifiedCollectionType.toWynimeSubjectCollectionType(): CollectionTypeDto? {
    return when (this) {
        UnifiedCollectionType.WISH -> CollectionTypeDto.WISH
        UnifiedCollectionType.DOING -> CollectionTypeDto.DOING
        UnifiedCollectionType.DONE -> CollectionTypeDto.DONE
        UnifiedCollectionType.ON_HOLD -> CollectionTypeDto.ON_HOLD
        UnifiedCollectionType.DROPPED -> CollectionTypeDto.DROPPED
        UnifiedCollectionType.NOT_COLLECTED -> null
    }
}
