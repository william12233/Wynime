package com.wynime.app.data.repository.episode

import androidx.paging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.network.EpisodeService
import com.wynime.app.data.network.toBangumiEpType
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionDao
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionEntity
import com.wynime.app.data.persistent.database.dao.SubjectCollectionDao
import com.wynime.app.data.persistent.database.dao.SubjectCollectionEntity
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.subject.toEpisodeType
import com.wynime.app.data.repository.subject.toUnifiedCollectionType
import com.wynime.app.domain.episode.EpisodeCollections
import com.wynime.models.EpisodeCollectionDto
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.currentTimeMillis
import com.wynime.utils.serialization.BigNum
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class EpisodeCollectionRepository(
    private val subjectDao: SubjectCollectionDao,
    private val episodeCollectionDao: EpisodeCollectionDao,
    private val episodeService: EpisodeService,
    subjectCollectionRepository: Lazy<SubjectCollectionRepository>,
    private val getEpisodeTypeFiltersUseCase: GetEpisodeTypeFiltersUseCase,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
    private val cacheExpiry: Duration = 1.hours,
) : Repository(defaultDispatcher) {

    private val subjectCollectionRepository by subjectCollectionRepository

    private fun EpisodeCollectionEntity.isExpired(): Boolean {
        return (currentTimeMillis() - lastFetched).milliseconds > cacheExpiry
    }

    private fun SubjectCollectionEntity.isExpired(): Boolean {
        return (currentTimeMillis() - lastFetched).milliseconds > cacheExpiry
    }

    fun episodeCollectionInfoFlow(subjectId: Int, episodeId: Int): Flow<EpisodeCollectionInfo> {
        return episodeCollectionDao.findByEpisodeId(episodeId).map { entity ->
            entity?.takeIf { !it.isExpired() }
                ?.toEpisodeCollectionInfo()
                ?: kotlin.run {
                    episodeService.getEpisodeCollectionById(subjectId, episodeId)
                        ?.also {
                            episodeCollectionDao.upsert(it.toEntity(subjectId))
                        }
                        ?: throw NoSuchElementException("Episode $episodeId not found")
                }
        }.flowOn(defaultDispatcher)
    }

    fun subjectEpisodeCollectionInfosFlow(
        subjectId: Int,
    ): Flow<List<EpisodeCollectionInfo>> = subjectCollectionRepository.subjectCollectionFlow(subjectId).map {
        it.episodes
    }.flowOn(defaultDispatcher)

    private suspend fun shouldUseCache(
        allowCached: Boolean,
        cachedEpisodes: List<EpisodeCollectionEntity>,
        subjectId: Int,
    ): Boolean {
        if (!allowCached) return false
        if (cachedEpisodes.isEmpty()) {
            subjectDao.findById(subjectId).first()
                ?.takeIf { !it.isExpired() }
                ?.totalEpisodes
                ?:
                return false

            return false
        }

        val lastUpdated = cachedEpisodes.maxOf { it.lastFetched }
        return (currentTimeMillis() - lastUpdated).milliseconds <= cacheExpiry
    }

    fun subjectEpisodeCollectionsPager(
        subjectId: Int,
        pagingConfig: PagingConfig = defaultPagingConfig,
    ): Flow<PagingData<EpisodeCollectionInfo>> = Pager(
        config = pagingConfig,
        remoteMediator = EpisodeCollectionsRemoteMediator(
            episodeCollectionDao, episodeService,
            subjectId,
        ),
        pagingSourceFactory = {
            episodeCollectionDao.filterBySubjectIdPaging(subjectId)
        },
    ).flow.map { data ->
        data.map {
            it.toEpisodeCollectionInfo()
        }
    }.flowOn(defaultDispatcher)

    suspend fun setAllEpisodesWatched(subjectId: Int) = withContext(defaultDispatcher) {
        val episodeIds = subjectEpisodeCollectionInfosFlow(subjectId)
            .first()
            .map { it.episodeId }

        episodeService.setEpisodeCollection(subjectId, episodeIds, UnifiedCollectionType.DONE)
        episodeCollectionDao.setAllEpisodesWatched(subjectId)
    }

    suspend fun setEpisodeCollectionType(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType,
    ) = withContext(defaultDispatcher) {
        if (subjectCollectionRepository.subjectCollectionFlow(subjectId)
                .first().collectionType == UnifiedCollectionType.NOT_COLLECTED
        ) {
            logger.warn { "User has not yet collected subject $subjectId when we want to setEpisodeCollectionType, ignoring." }

        }
        episodeService.setEpisodeCollection(subjectId, listOf(episodeId), collectionType)
        episodeCollectionDao.updateSelfCollectionType(subjectId, episodeId, collectionType)
    }

    suspend fun getEpisodeCollectionType(
        subjectId: Int,
        episodeId: Int,
        allowNetwork: Boolean,
    ): UnifiedCollectionType? = withContext(defaultDispatcher) {
        try {
            val local = episodeCollectionDao.findByEpisodeId(episodeId).first()

            if (local != null && (!local.isExpired() || !allowNetwork)) {
                return@withContext local.selfCollectionType
            } else {
                val remote = episodeService.getEpisodeCollectionById(subjectId, episodeId)
                if (remote != null) {
                    return@withContext remote.collectionType
                }

                return@withContext null
            }
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    fun subjectCompletedFlow(subjectId: Int): Flow<Boolean> {
        return subjectEpisodeCollectionInfosFlow(subjectId)
            .combine(subjectCollectionRepository.subjectCollectionFlow(subjectId)) { epCollection, subject ->
                EpisodeCollections.isSubjectCompleted(epCollection.map { it.episodeInfo }, subject.recurrence)
            }
    }

    private inner class EpisodeCollectionsRemoteMediator<T : Any>(
        private val episodeCollectionDao: EpisodeCollectionDao,
        private val episodeService: EpisodeService,
        val subjectId: Int,
    ) : RemoteMediator<Int, T>() {
        override suspend fun initialize(): InitializeAction {
            return withContext(defaultDispatcher) {
                if ((currentTimeMillis() - episodeCollectionDao.lastFetched(subjectId)).milliseconds > cacheExpiry) {
                    InitializeAction.LAUNCH_INITIAL_REFRESH
                } else {
                    InitializeAction.SKIP_INITIAL_REFRESH
                }
            }
        }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, T>,
        ): MediatorResult = withContext(defaultDispatcher) {
            val offset = when (loadType) {
                LoadType.REFRESH -> 0
                LoadType.PREPEND -> return@withContext MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> state.pages.size * state.config.pageSize
            }

            try {
                val episodeTypes = getEpisodeTypeFiltersUseCase().first()
                val episodes = episodeService.getEpisodeCollectionInfosPaged(
                    subjectId,

                    episodeType = episodeTypes.singleOrNull()?.toBangumiEpType(),
                    offset = offset,
                    limit = state.config.pageSize,
                )
                episodes.page.filter { it.episodeInfo.type in episodeTypes }.takeIf { it.isNotEmpty() }?.let { list ->
                    episodeCollectionDao.upsert(
                        list.map { it.toEntity(subjectId) },
                    )
                }

                MediatorResult.Success(endOfPaginationReached = episodes.hasMore)
            } catch (e: Exception) {
                return@withContext MediatorResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }

        }
    }
}

suspend inline fun EpisodeCollectionRepository.setEpisodeWatched(subjectId: Int, episodeId: Int, watched: Boolean) =
    setEpisodeCollectionType(
        subjectId,
        episodeId,
        if (watched) UnifiedCollectionType.DONE else UnifiedCollectionType.WISH,
    )

fun EpisodeCollectionInfo.toEntity(
    subjectId: Int,
    lastFetched: Long = currentTimeMillis(),
): EpisodeCollectionEntity {
    return EpisodeCollectionEntity(
        subjectId = subjectId,
        episodeId = episodeId,
        episodeType = episodeInfo.type,
        name = episodeInfo.name,
        nameCn = episodeInfo.nameCn,
        airDate = episodeInfo.airDate,
        comment = episodeInfo.comment,
        desc = episodeInfo.desc,
        sort = episodeInfo.sort,
        sortNumber = episodeInfo.sort.number ?: Float.MAX_VALUE,
        ep = episodeInfo.ep,
        imageMedium = episodeInfo.imageMedium,
        imageLarge = episodeInfo.imageLarge,
        selfCollectionType = collectionType,
        lastFetched = lastFetched,
    )
}

fun EpisodeCollectionEntity.toEpisodeCollectionInfo() =
    EpisodeCollectionInfo(
        episodeInfo = toEpisodeInfo(),
        collectionType = selfCollectionType,
    )

fun EpisodeCollectionDto.toEpisodeCollectionInfo() =
    EpisodeCollectionInfo(
        episodeInfo = toEpisodeInfo(),
        collectionType = collectionType.toUnifiedCollectionType(),
    )

private fun EpisodeCollectionEntity.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = this.episodeId,
        type = this.episodeType,
        name = this.name,
        nameCn = this.nameCn,
        airDate = this.airDate,
        comment = this.comment,
        desc = this.desc,
        sort = this.sort,
        ep = this.ep,
        imageMedium = this.imageMedium,
        imageLarge = this.imageLarge,
    )
}

private fun EpisodeCollectionDto.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = this.episodeId.toInt(),
        type = this.type.toEpisodeType(),
        name = this.name,
        nameCn = this.nameCn,
        airDate = this.airdate?.let { PackedDate.parseFromDate(it) } ?: PackedDate.Invalid,
        comment = 0,
        desc = this.description,
        sort = EpisodeSort(BigNum(this.sort), this.type.toEpisodeType()),
        ep = this.ep?.let { EpisodeSort(BigNum(it), this.type.toEpisodeType()) },
        imageMedium = this.imageMedium,
        imageLarge = this.imageLarge,
    )
}
