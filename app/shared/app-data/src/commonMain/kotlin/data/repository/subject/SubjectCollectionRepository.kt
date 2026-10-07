/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

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
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.data.models.subject.SubjectTmdbArt
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.models.subject.TmdbImage
import me.him188.ani.app.data.network.EpisodeService
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.network.SubjectCollectionPage
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionDao
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.persistent.database.dao.SubjectRelations
import me.him188.ani.app.data.persistent.database.dao.SubjectRelationsDao
import me.him188.ani.app.data.persistent.database.dao.deleteAll
import me.him188.ani.app.data.persistent.database.dao.filterMostRecentUpdated
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.toEpisodeCollectionInfo
import me.him188.ani.app.data.repository.shouldRetry
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.checkAccessBangumiApiNow
import me.him188.ani.app.domain.session.restartOnNewLogin
import me.him188.ani.client.models.AniAnimeRecurrence
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRelations
import me.him188.ani.client.models.AniTag
import me.him188.ani.client.models.AniTmdbImage
import me.him188.ani.client.models.AniTmdbSubjectArt
import me.him188.ani.client.models.AniUpdateSubjectCollectionRequest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.processing.toSubjectCollectionType
import me.him188.ani.utils.coroutines.combine
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.serialization.BigNum
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * 条目信息和条目收藏的仓库.
 *
 * [SubjectInfo], [SubjectCollectionInfo], [SubjectCollectionCounts]
 *
 * 是 abstract 而不是 sealed: 生产实现只有 [SubjectCollectionRepositoryImpl], 但 Bangumi 收藏合并相关的测试 (其他模块) 需要用轻量的 fake 替代它.
 */
abstract class SubjectCollectionRepository(
    defaultDispatcher: CoroutineContext = Dispatchers.Default
) : Repository(defaultDispatcher) {
    /**
     * 获取条目收藏统计信息 cold [Flow]. Flow 将会 emit 至少一个值, 失败时 emit `null`.
     */
    abstract fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts?>

    abstract fun subjectCollectionFlow(subjectId: Int): Flow<SubjectCollectionInfo>

    abstract fun subjectCollectionsPager(
        query: CollectionsFilterQuery = CollectionsFilterQuery.Empty,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 30,
            prefetchDistance = 30,
        ),
    ): Flow<PagingData<SubjectCollectionInfo>>

    /**
     * 获取本地所有缓存的 [SubjectCollectionInfo] 的 [subjectId][SubjectCollectionInfo.subjectId]
     */
    abstract fun cachedValidSubjectIds(): Flow<List<Int>>

    /**
     * 更新根据服务器上记录的最近有修改的条目收藏. 也就是用户最近操作过的条目收藏.
     */
    abstract suspend fun updateRecentlyUpdatedSubjectCollections(
        limit: Int,
        type: UnifiedCollectionType?,
        offset: Int = 0,
    )

    /**
     * 获取最近更新的条目收藏 cold [Flow].
     */
    abstract fun mostRecentlyUpdatedSubjectCollectionsFlow(
        limit: Int,
        types: List<UnifiedCollectionType>? = null, // null for all
    ): Flow<List<SubjectCollectionInfo>>

    /**
     * @param score 0 to remove rating
     * @param comment set empty to remove
     * @param tags set empty to remove
     */
    abstract suspend fun updateRating(
        subjectId: Int,
        score: Int? = null,
        comment: String? = null,
        tags: List<String>? = null,
        isPrivate: Boolean? = null,
    )

    /**
     * @throws me.him188.ani.app.data.repository.RepositoryAuthorizationException
     */
    abstract suspend fun setSubjectCollectionTypeOrDelete(
        subjectId: Int,
        type: UnifiedCollectionType?,
    )

    /**
     * 只从本地数据库中获取收藏类型, 不进行网络请求.
     */
    abstract fun getSubjectCollectionTypeOffline(subjectId: Int): Flow<UnifiedCollectionType?>

    /**
     * 只从本地数据库中获取条目的展示信息 (名称/封面/总集数), 不进行网络请求.
     * 未收藏 (本地无记录) 时 emit `null`.
     */
    abstract fun getSubjectDisplayInfoOffline(subjectId: Int): Flow<OfflineSubjectDisplayInfo?>

    abstract suspend fun getSubjectIdsByCollectionType(types: List<UnifiedCollectionType>): Flow<List<Int>>

    abstract suspend fun getSubjectNamesCnByCollectionType(types: List<UnifiedCollectionType>): Flow<List<String>>

    abstract suspend fun performBangumiFullSync()

    abstract suspend fun getBangumiFullSyncState(): BangumiSyncState?

    open suspend fun getBangumiFullSyncSummary(): BangumiFullSyncSummary? = null

    /**
     * 使 [subjectIds] 对应条目的本地缓存失效, 并立即从服务端重新拉取这些条目 (并行度有限, 见实现):
     * - 服务端仍有收藏 → 用服务端的值覆盖本地行与剧集缓存 (正在展示的收藏列表随之更新);
     * - 服务端已无收藏 (条目不存在或未收藏) → 删除本地行 (剧集缓存随之级联删除);
     * - 网络失败 → 保留本地行 (绝不因失败删除), 只将其 `lastFetched` 置 0, 下次访问时重新拉取;
     *   首次失败后不再对剩余条目发起新的拉取 (多半是断网, 逐个等待超时会让 "应用合并" 长时间转圈), 已发起的照常完成.
     *
     * 之后将所有条目的 `lastFetched` 置 0 (下次创建收藏列表分页器时从服务端刷新), 并发出 [collectionsInvalidated].
     *
     * [subjectIds] 为空时不做任何事.
     *
     * 用于服务端解决 Bangumi 收藏冲突之后: 这些条目在服务端的值已经改变, 本地缓存不再可信.
     */
    abstract suspend fun invalidateCache(subjectIds: List<Int>)

    /**
     * 将所有条目的 `lastFetched` 置 0 (不删除本地数据), 使下次进入收藏页或条目页时从服务端刷新, 并发出 [collectionsInvalidated].
     *
     * 用于服务端 Bangumi 全量同步 (对账) 完成之后: 自动合并的结果已写入服务端, 本地缓存可能过期.
     */
    abstract suspend fun invalidateAllCaches()

    private val _collectionsInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * [invalidateCache] / [invalidateAllCaches] 完成后发出一次, 供已经创建的收藏列表分页器重新加载.
     *
     * 分页器只在创建时 (`RemoteMediator.initialize`) 根据 `lastFetched` 决定是否从服务端刷新, 已在展示的列表不会因为
     * `lastFetched` 被置 0 而自动刷新; 收藏页的 ViewModel 收集此流并重建分页器.
     */
    val collectionsInvalidated: SharedFlow<Unit> = _collectionsInvalidated.asSharedFlow()

    /**
     * [collectionsInvalidated] 当前的订阅者数. 仅测试用: 等 ViewModel 订阅之后再触发失效, 否则事件没有订阅者会被丢弃.
     */
    @TestOnly
    val collectionsInvalidatedSubscriptionCount: StateFlow<Int>
        get() = _collectionsInvalidated.subscriptionCount

    /**
     * 缓存失效完成后调用, 发出 [collectionsInvalidated]. 没有订阅者时直接丢弃; 订阅者来不及处理时多次失效合并为一次.
     */
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
    private val animeScheduleRepository: AnimeScheduleRepository,
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
                    // 不管是不是过期都先 emit, 确保离线时能播放
                    emit(existing)
                }

                // 如果没有缓存, 则 fetch 然后插入 subject 缓存
                if (existing == null || existing.isExpired()) {
                    refetchSubjectCollection(subjectId)
                    // TODO: 2025/5/24 handle subject not found 
                }
            }
            .filterNotNull()
            // 有 subject 缓存后才能从 episodeCollectionRepository fetch episodes
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

    /**
     * 从服务端拉取条目 (含用户的收藏状态与剧集) 并写入本地缓存: 覆盖同 id 的旧行 (`lastFetched` 为当前时间), 删除本地多余的剧集.
     *
     * @return 服务端返回的条目; 条目不存在 (404) 时为 `null`, 此时不写入任何东西.
     */
    private suspend fun refetchSubjectCollection(subjectId: Int): AniSubjectCollection? {
        val subject = subjectService.getSubjectCollection(subjectId) ?: return null
        val lastFetched = currentTimeMillis()
        val subjectEntity = subject.toEntity(lastFetched = lastFetched)
        val tombstone = trackingMetadataRepository?.find(subjectId)
        if (tombstone?.localDeletedAt != null && subjectEntity.lastUpdated <= tombstone.localDeletedAt) {
            val protectedEntity = subjectCollectionDao.findById(subjectId).first()
                ?.copy(
                    collectionType = UnifiedCollectionType.NOT_COLLECTED,
                    lastUpdated = maxOf(tombstone.localDeletedAt, subjectEntity.lastUpdated),
                    lastFetched = lastFetched,
                )
                ?: subjectEntity.copy(
                    collectionType = UnifiedCollectionType.NOT_COLLECTED,
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

        // 更新剧集列表
        val oldIds = episodeCollectionDao.listIdBySubjectId(subjectId).first().toMutableList()
        episodeCollectionDao.upsert(episodeEntities)
        for (newEntity in episodeEntities) {
            oldIds.remove(newEntity.episodeId)
        }
        if (oldIds.isNotEmpty()) { // 删除本地存的多余的剧集 (通常没有)
            episodeCollectionDao.deleteAllByEpisodeIds(subjectId, oldIds)
        }
        return subject
    }

    private suspend fun saveEpisodeEntities(
        subjectId: Int,
        episodes: List<AniEpisodeCollection>,
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
        types: List<UnifiedCollectionType>?, // null for all
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
                // 只允许同时一个请求. 防止多个请求浪费带宽.
                // 一般来说不会有多个请求. 最常见的并行请求可能是用户刚刚打开 APP 进入探索页自动刷新"继续观看"栏目, 在刷新还在进行时切换到收藏页触发自动刷新.
                updateRecentlyUpdatedSubjectCollectionsMutex.withLock {
                    fetchAndSaveSubjectCollectionsWithEpisodes(type, limit, offset)
                }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    // transparent exception
    /**
     * 执行网络查询条目收藏及其剧集列表, 在所有网络请求都成功后调用 [onFetched], 然后保存查询结果到数据库.
     *
     * @param onFetched 当所有网络请求都成功后调用
     */
    private suspend inline fun fetchAndSaveSubjectCollectionsWithEpisodes(
        type: UnifiedCollectionType?,
        limit: Int,
        offset: Int,
        onFetched: (page: SubjectCollectionPage) -> Unit = {},
    ) {
        require(type != UnifiedCollectionType.NOT_COLLECTED) { "type must not be NOT_COLLECTED" }
        require(limit > 0) { "limit must be positive" }

        // 执行网络请求查询好需要的 subject 和 episodes
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

        // 批量插入条目信息
        val lastFetched = currentTimeMillis()
        saveSubjectCollectionsWithEpisodes(page.items, lastFetched)
        onFetched(page)
    }

    /**
     * 將一次成功取得的 Bangumi 收藏頁寫入本機快取。
     *
     * 先寫入條目再寫入集數，並清理同一條目已不存在的舊集數，避免外鍵與過期觀看狀態殘留。
     */
    private suspend fun saveSubjectCollectionsWithEpisodes(
        items: List<AniSubjectCollection>,
        lastFetched: Long,
    ) {
        val entities = ArrayList<SubjectCollectionEntity>(items.size)
        for (item in items) {
            val entity = item.toEntity(lastFetched = lastFetched)
            val tombstone = trackingMetadataRepository?.find(entity.subjectId)
            if (tombstone?.localDeletedAt != null && entity.lastUpdated <= tombstone.localDeletedAt) {
                // Collection refreshes can still return a remote row that the user cancelled
                // locally. Keep the detail cache, but never let that stale remote row re-enter
                // collection lists.
                entities += entity.copy(
                    collectionType = UnifiedCollectionType.NOT_COLLECTED,
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

        // 必须先插入好条目信息, 否则插入 episode 会 foreign key constraint failed
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
        score: Int?, // 0 to remove rating
        comment: String?, // set empty to remove
        tags: List<String>?,
        isPrivate: Boolean?,
    ) {
        withContext(defaultDispatcher) {
            subjectService.patchSubjectCollection(
                subjectId,
                AniUpdateSubjectCollectionRequest(
                    selfRating = AniSelfRatingInfo(
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
                // Keep the local row as NOT_COLLECTED for immediate UI updates and persist a
                // durable DELETE_COLLECTION tombstone for the authenticated sync route.
                subjectCollectionDao.updateType(
                    subjectId = subjectId,
                    collectionType = UnifiedCollectionType.NOT_COLLECTED,
                    lastUpdated = now,
                    lastFetched = now,
                )
                trackingMetadataRepository?.markLocalDeletion(subjectId, now)
            } else {
                val updated = subjectCollectionDao.updateType(
                    subjectId = subjectId,
                    collectionType = type,
                    lastUpdated = now,
                    lastFetched = now,
                )
                if (updated == 0) {
                    // A missing detail cache is hydrated only when necessary; the local mutation
                    // is still recorded before the optional network-backed hydration completes.
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
                    val remoteCollections = ArrayList<AniSubjectCollection>()
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
                        watchedEpisodeCount += remote.episodes.count { it.collectionType == AniEpisodeCollectionType.DONE }
                        try {
                            val tombstone = trackingMetadataRepository?.find(subjectId)
                            if (tombstone?.localDeletedAt != null &&
                                remoteEntity.lastUpdated <= tombstone.localDeletedAt
                            ) {
                                subjectCollectionDao.updateType(
                                    subjectId = subjectId,
                                    collectionType = UnifiedCollectionType.NOT_COLLECTED,
                                    lastUpdated = tombstone.localDeletedAt,
                                    lastFetched = lastFetched,
                                )
                            } else {
                                if (tombstone?.localDeletedAt != null) {
                                    trackingMetadataRepository.clearTombstone(subjectId, remoteEntity.lastUpdated)
                                }
                                // Bangumi updatedAt 是收藏狀態的版本時間；較舊的回應不可覆蓋較新的本機狀態。
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
                // 有限并行: 解决冲突后通常要重新拉取几十个条目 (每个都带完整剧集列表), 串行会让 "应用合并" 等几十个 RTT.
                val semaphore = Semaphore(INVALIDATE_REFETCH_PARALLELISM)
                // 首次网络失败后不再发起新的拉取: 断网时每个请求都要等到连接超时, 剩余行由下面的 resetAllLastFetched 覆盖.
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
                                // 网络失败: 保留本地行, 靠下面的 resetAllLastFetched 让它下次重新拉取.
                                // 绝不因失败删除, 否则条目会从正在展示的收藏列表里消失.
                                failed.value = true
                                logger.warn(e) { "Failed to refetch subject collection $subjectId after invalidation, keeping the cached row and skipping the remaining refetches" }
                                return@withPermit
                            }
                            if (fetched == null || fetched.collectionType == null) {
                                val tombstone = trackingMetadataRepository?.find(subjectId)
                                if (tombstone?.localDeletedAt != null) {
                                    // A local cancellation remains visible to the detail flow even
                                    // when Bangumi still returns no collection for the subject.
                                    subjectCollectionDao.updateType(
                                        subjectId = subjectId,
                                        collectionType = UnifiedCollectionType.NOT_COLLECTED,
                                        lastUpdated = tombstone.localDeletedAt,
                                        lastFetched = currentTimeMillis(),
                                    )
                                } else {
                                    // A normal remote absence keeps the historical invalidation
                                    // behaviour and removes the cache row with its episode cache.
                                    subjectCollectionDao.delete(subjectId)
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            // 分页器创建时只看最新的 lastFetched 决定是否从服务端刷新; 已在展示的分页器由 collectionsInvalidated 触发重建
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

        /**
         * [invalidateCache] 重新拉取条目的最大并行数.
         */
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
//        isOnAir = ,
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
                    // 没有更多数据了
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
            //                        logger.warn { "Mediator APPEND, lastLoadedPage ${}" }
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

fun AniSubjectCollection.toEntity(
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

private fun AniTmdbSubjectArt.toSubjectTmdbArt(): SubjectTmdbArt = SubjectTmdbArt(
    backdrops = backdrops.map { it.toTmdbImage() },
    posters = posters.mapValues { it.value.toTmdbImage() },
    logos = logos.mapValues { it.value.toTmdbImage() },
)

private fun AniTmdbImage.toTmdbImage(): TmdbImage = TmdbImage(medium = medium, large = large, vector = vector)

/**
 * 本地数据库中缓存的条目展示信息.
 * @see SubjectCollectionRepository.getSubjectDisplayInfoOffline
 */
data class OfflineSubjectDisplayInfo(
    val subjectId: Int,
    val displayName: String,
    val imageLarge: String,
    /** 列表用封面, 没有缩略图时与 [imageLarge] 相同. */
    val imageThumb: String,
    val totalEpisodes: Int,
)

fun AniSubjectRelations.toSubjectRelationsEntity(): SubjectRelations {
    return SubjectRelations(
        seriesMainSubjectIds,
        seriesMainSubjectNames,
        sequelSubjects,
        sequelSubjectNames,
    )
}

fun AniTag.toTag(): Tag = Tag(
    name = name,
    count = count,
)

fun AniFavourite.toSubjectCollectionStats(): SubjectCollectionStats {
    return SubjectCollectionStats(
        wish = wish,
        doing = doing,
        done = done,
        onHold = onHold,
        dropped = dropped,
    )
}

fun AniAnimeRecurrence.toSubjectRecurrence(): SubjectRecurrence? {
    return SubjectRecurrence(
        Instant.parse(startTime),
        interval = intervalMillis.milliseconds,
    )
}

fun AniCollectionType?.toUnifiedCollectionType(): UnifiedCollectionType {
    return when (this) {
        AniCollectionType.WISH -> UnifiedCollectionType.WISH
        AniCollectionType.DOING -> UnifiedCollectionType.DOING
        AniCollectionType.DONE -> UnifiedCollectionType.DONE
        AniCollectionType.ON_HOLD -> UnifiedCollectionType.ON_HOLD
        AniCollectionType.DROPPED -> UnifiedCollectionType.DROPPED
        null -> UnifiedCollectionType.NOT_COLLECTED
    }
}

fun AniEpisodeCollection.toEntity1(
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

fun AniEpisodeType.toEpisodeType(): EpisodeType? {
    return when (this) {
        AniEpisodeType.MAIN -> EpisodeType.MainStory
        AniEpisodeType.SPECIAL -> EpisodeType.SP
        AniEpisodeType.OP -> EpisodeType.OP
        AniEpisodeType.ED -> EpisodeType.ED
        AniEpisodeType.TRAILER -> EpisodeType.PV
        AniEpisodeType.MAD -> EpisodeType.MAD
        AniEpisodeType.OTHER -> null
    }
}

fun AniEpisodeCollectionType?.toUnifiedCollectionType(): UnifiedCollectionType {
    return when (this) {
        null -> UnifiedCollectionType.NOT_COLLECTED
        AniEpisodeCollectionType.DONE -> UnifiedCollectionType.DONE
    }
}

fun AniSelfRatingInfo.toSelfRatingInfo(): SelfRatingInfo {
    return SelfRatingInfo(
        score = score, comment = comment, tags = tags, isPrivate = isPrivate,
    )
}

fun UnifiedCollectionType.toAniSubjectCollectionType(): AniCollectionType? {
    return when (this) {
        UnifiedCollectionType.WISH -> AniCollectionType.WISH
        UnifiedCollectionType.DOING -> AniCollectionType.DOING
        UnifiedCollectionType.DONE -> AniCollectionType.DONE
        UnifiedCollectionType.ON_HOLD -> AniCollectionType.ON_HOLD
        UnifiedCollectionType.DROPPED -> AniCollectionType.DROPPED
        UnifiedCollectionType.NOT_COLLECTED -> null
    }
}
