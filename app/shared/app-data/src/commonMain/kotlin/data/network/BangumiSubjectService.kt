package com.wynime.app.data.network

import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.subject.CharacterInfo
import com.wynime.app.data.models.subject.CharacterRole
import com.wynime.app.data.models.subject.PersonCareer
import com.wynime.app.data.models.subject.PersonInfo
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.models.subject.PersonType
import com.wynime.app.data.models.subject.RelatedCharacterInfo
import com.wynime.app.data.models.subject.RelatedPersonInfo
import com.wynime.app.data.models.subject.SubjectCollectionCounts
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryRateLimitedException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.utils.platform.currentTimeMillis
import com.wynime.models.CollectionTypeDto
import com.wynime.models.EpisodeCollectionDto
import com.wynime.models.EpisodeCollectionTypeDto
import com.wynime.models.EpisodeTypeDto
import com.wynime.models.FavouriteDto
import com.wynime.models.SelfRatingInfoDto
import com.wynime.models.SubjectAiringInfoDto
import com.wynime.models.SubjectCollectionDto
import com.wynime.models.SubjectRecommendationDto
import com.wynime.models.SubjectRelationsDto
import com.wynime.models.TagDto
import com.wynime.models.UpdateSubjectCollectionRequestDto
import com.wynime.datasources.bangumi.models.BangumiCollection
import com.wynime.datasources.bangumi.models.BangumiCount
import com.wynime.datasources.bangumi.models.BangumiEpisode
import com.wynime.datasources.bangumi.models.BangumiEpisodeCollectionType
import com.wynime.datasources.bangumi.models.BangumiPerson
import com.wynime.datasources.bangumi.models.BangumiPersonCareer
import com.wynime.datasources.bangumi.models.BangumiRelatedCharacter
import com.wynime.datasources.bangumi.models.BangumiRelatedPerson
import com.wynime.datasources.bangumi.models.BangumiSubject
import com.wynime.datasources.bangumi.models.BangumiSubjectCollectionType
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiUserSubjectCollection
import com.wynime.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import com.wynime.datasources.bangumi.models.BangumiV0SubjectRelation
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlin.coroutines.CoroutineContext

class BangumiSubjectService(
    private val bangumiApi: BangumiApiProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val collectionRemovalService: CollectionRemovalService? = null,
) : SubjectService {
    private val logger = logger<BangumiSubjectService>()
    val subjectCountStatsRestarter = FlowRestarter()
    private val syncRequestThrottle = BangumiSyncRequestThrottle()

    override suspend fun getSubjectCollections(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int,
    ): List<SubjectCollectionDto> = getSubjectCollectionsPage(type, offset, limit).also { page ->
        if (page.omittedSubjectIds.isNotEmpty()) {
            throw RepositoryRequestError(
                "Bangumi 收藏頁缺少條目：${page.omittedSubjectIds.joinToString()}",
            )
        }
    }.items

    override suspend fun getSubjectCollectionsPage(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int,
        onItemHydrated: suspend (completed: Int, total: Int) -> Unit,
    ): SubjectCollectionPage = withContext(ioDispatcher) {
        val username = bangumiApi.currentUsername()
            ?: return@withContext SubjectCollectionPage(emptyList(), offset, limit, 0)
        val page = syncRequestThrottle.run {
            bangumiApi.request {
                getUserCollectionsByUsername(
                    username = username,
                    subjectType = BangumiSubjectType.Anime,
                    type = type,
                    limit = limit,
                    offset = offset,
                )
            }
        }
        val collections = page.data.orEmpty()
        if (page.total == 0 && collections.isNotEmpty()) {
            throw RepositoryRequestError(
                "Bangumi 收藏頁 total=0 但回傳了 ${collections.size} 筆資料：offset=$offset",
            )
        }
        logger.info {
            "Bangumi sync collection page offset=$offset raw=${collections.size} " +
                "total=${page.total} subjectWorkers=$MAX_SYNC_SUBJECT_WORKERS"
        }
        val hydrationSemaphore = Semaphore(MAX_SYNC_SUBJECT_WORKERS)
        val workerStateMutex = Mutex()
        var activeWorkers = 0
        var maxActiveWorkers = 0
        var hydratedCount = 0
        val hydrated = supervisorScope {
            collections.map { collection ->
                async {
                    hydrationSemaphore.withPermit {
                        workerStateMutex.withLock {
                            activeWorkers++
                            maxActiveWorkers = maxOf(maxActiveWorkers, activeWorkers)
                        }
                        try {
                            buildSubjectCollection(
                                subjectId = collection.subjectId,
                                collection = collection,
                                username = username,
                                throttle = syncRequestThrottle,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            logger.warn(e) {
                                "Failed to hydrate Bangumi subject ${collection.subjectId}; " +
                                    "the page will report a partial result"
                            }
                            null
                        } finally {
                            workerStateMutex.withLock { activeWorkers-- }
                        }
                        .also {
                            val completed = workerStateMutex.withLock { ++hydratedCount }
                            onItemHydrated(completed, collections.size)
                        }
                    }
                }
            }.awaitAll()
        }
        val result = SubjectCollectionPage(
            items = hydrated.filterNotNull(),
            offset = offset,
            requestedLimit = limit,
            total = page.total,
            omittedSubjectIds = collections.filterIndexed { index, _ -> hydrated[index] == null }
                .map { it.subjectId },
            sourceItemCount = collections.size,
        )
        logger.info {
            "Bangumi sync collection page completed offset=$offset raw=${collections.size} " +
                "hydrated=${result.items.size} omitted=${result.omittedSubjectIds.size} " +
                "maxActiveWorkers=$maxActiveWorkers"
        }
        result
    }

    private suspend fun <T> throttled(
        throttle: BangumiSyncRequestThrottle?,
        block: suspend () -> T,
    ): T {
        return if (throttle == null) block() else throttle.run(block)
    }

    private suspend fun requestSubject(
        throttle: BangumiSyncRequestThrottle?,
        subjectId: Int,
    ): BangumiSubject {
        return throttled(throttle) {
            bangumiApi.request { getSubjectById(subjectId) }
        }
    }

    override suspend fun getSubjectCollection(subjectId: Int): SubjectCollectionDto? =
        withContext(ioDispatcher) {
            buildSubjectCollection(subjectId, username = bangumiApi.currentUsername())
        }

    override suspend fun getSubjectRelations(
        subjectId: Int,
        withCharacterActors: Boolean,
    ): BatchSubjectRelations = withContext(ioDispatcher) {
        val characters = bangumiApi.request { getRelatedCharactersBySubjectId(subjectId) }
        val persons = bangumiApi.request { getRelatedPersonsBySubjectId(subjectId) }
        BatchSubjectRelations(
            subjectId = subjectId,
            relatedCharacterInfoList = characters.mapIndexed { index, character ->
                character.toRelatedCharacterInfo(index, withCharacterActors)
            },
            relatedPersonInfoList = persons.mapIndexed { index, person ->
                person.toRelatedPersonInfo(index)
            },
        )
    }

    override fun subjectCollectionById(subjectId: Int): Flow<SubjectCollectionDto?> = flow {
        emit(getSubjectCollection(subjectId))
    }.flowOn(ioDispatcher)

    override suspend fun patchSubjectCollection(
        subjectId: Int,
        payload: UpdateSubjectCollectionRequestDto,
    ) = withContext(ioDispatcher) {
        try {
            if (!bangumiApi.hasAccessToken()) {
                throw RepositoryAuthorizationException("Bangumi access token is required")
            }
            val selfRating = payload.selfRating
            bangumiApi.request {
                postUserCollection(
                    subjectId = subjectId,
                    bangumiUserSubjectCollectionModifyPayload = BangumiUserSubjectCollectionModifyPayload(
                        type = payload.collectionType?.toBangumiCollectionType(),
                        rate = selfRating?.score,
                        comment = selfRating?.comment,
                        private = selfRating?.isPrivate,
                        tags = selfRating?.tags,
                    ),
                )
            }
            subjectCountStatsRestarter.restart()
        } catch (throwable: Throwable) {
            throw wrapBangumiCollectionException(throwable)
        }
    }

    override suspend fun deleteSubjectCollection(subjectId: Int) = withContext(ioDispatcher) {
        try {
            val removal = collectionRemovalService
                ?: throw RepositoryRequestError("取消收藏服務未設定")
            removal.confirm(subjectId)
            subjectCountStatsRestarter.restart()
        } catch (throwable: Throwable) {
            throw wrapBangumiCollectionException(throwable)
        }
    }

    private fun wrapBangumiCollectionException(throwable: Throwable): RepositoryException {
        if (throwable is RepositoryException) return throwable
        if (throwable is ClientRequestException) {
            return when (throwable.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> RepositoryAuthorizationException(throwable.response.status.description, throwable)

                HttpStatusCode.TooManyRequests -> RepositoryRateLimitedException(
                    throwable.response.status.description,
                    throwable,
                )

                else -> RepositoryRequestError(
                    localizedMessage = "Bangumi collection request failed (HTTP ${throwable.response.status.value}).",
                    cause = throwable,
                )
            }
        }
        return RepositoryException.wrapOrThrowCancellation(throwable)
    }

    override suspend fun getSubjectRecommendations(
        subjectId: Int,
        limit: Int,
    ): List<SubjectRecommendationDto> = withContext(ioDispatcher) {
        bangumiApi.request { getRelatedSubjectsBySubjectId(subjectId) }
            .take(limit)
            .map { relation ->
                SubjectRecommendationDto(
                    subjectName = relation.name,
                    subjectNameCn = relation.nameCn,
                    imageUrl = relation.images?.large.orEmpty(),
                    desc1 = relation.relation,
                    desc2 = "",
                    subjectId = relation.id.toLong(),
                    uri = null,
                )
            }
    }

    override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts> = flow {
        val username = bangumiApi.currentUsername()
        if (username == null) {
            emit(SubjectCollectionCounts(0, 0, 0, 0, 0, 0))
            return@flow
        }
        val totals = BangumiSubjectCollectionType.entries.map { type ->
            bangumiApi.request {
                getUserCollectionsByUsername(
                    username = username,
                    subjectType = BangumiSubjectType.Anime,
                    type = type,
                    limit = 1,
                    offset = 0,
                )
            }.total ?: 0
        }
        emit(
            SubjectCollectionCounts(
                wish = totals[0],
                done = totals[1],
                doing = totals[2],
                onHold = totals[3],
                dropped = totals[4],
                total = totals.sum(),
            ),
        )
    }.restartable(subjectCountStatsRestarter).flowOn(ioDispatcher)

    override suspend fun performBangumiFullSync() = Unit

    override suspend fun getBangumiFullSyncState(): BangumiSyncState = BangumiSyncState.Unsupported

    private suspend fun buildSubjectCollection(
        subjectId: Int,
        collection: BangumiUserSubjectCollection? = null,
        username: String? = null,
        throttle: BangumiSyncRequestThrottle? = null,
    ): SubjectCollectionDto? {
        val subject = try {
            requestSubject(throttle, subjectId)
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) return null
            throw e
        }
        val userCollection = collection ?: loadUserCollection(subjectId, username, throttle)
        val episodes = loadEpisodes(subjectId, throttle)
        val episodeCollections = loadEpisodeCollections(subjectId, throttle)

        return subject.toWynimeSubjectCollection(
            collection = userCollection,
            episodes = episodes,
            episodeCollections = episodeCollections,
        )
    }

    private suspend fun loadUserCollection(
        subjectId: Int,
        username: String? = null,
        throttle: BangumiSyncRequestThrottle? = null,
    ): BangumiUserSubjectCollection? {
        val currentUsername = username ?: bangumiApi.currentUsername() ?: return null
        return try {
            throttled(throttle) {
                bangumiApi.request { getUserCollection(username = currentUsername, subjectId = subjectId) }
            }
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound ||
                e.response.status == HttpStatusCode.Unauthorized ||
                e.response.status == HttpStatusCode.Forbidden
            ) {
                null
            } else {
                throw e
            }
        }
    }

    private suspend fun loadEpisodes(
        subjectId: Int,
        throttle: BangumiSyncRequestThrottle? = null,
    ): List<BangumiEpisode> {
        val result = ArrayList<BangumiEpisode>()
        val seenEpisodeIds = HashSet<Int>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = throttled(throttle) {
                bangumiApi.request {
                    getEpisodes(subjectId = subjectId, limit = limit, offset = offset)
                }
            }
            val data = page.data.orEmpty()
            if (data.any { !seenEpisodeIds.add(it.id) }) {
                throw RepositoryRequestError("Bangumi 集數資料含有重複 episode：subject=$subjectId，offset=$offset")
            }
            result += data
            val total = page.total
            if (total != null && result.size > total) {
                throw RepositoryRequestError(
                    "Bangumi 集數資料超過 total：subject=$subjectId，取得 ${result.size}/$total",
                )
            }
            if (data.isEmpty()) {
                if (total != null && result.size < total) {
                    throw RepositoryRequestError("Bangumi 集數資料不完整：取得 ${result.size}/$total")
                }
                break
            }
            if (total != null && result.size >= total) break
            if (data.size < limit) {
                if (total == null) break
                throw RepositoryRequestError("Bangumi 集數分頁提前結束：subject=$subjectId，取得 ${result.size}/$total")
            }
            val nextOffset = offset + data.size
            if (nextOffset <= offset) throw RepositoryRequestError("Bangumi 集數分頁 offset 未前進")
            offset = nextOffset
        }
        return result
    }

    private suspend fun loadEpisodeCollections(
        subjectId: Int,
        throttle: BangumiSyncRequestThrottle? = null,
    ): Map<Int, BangumiEpisodeCollectionType> {
        if (!bangumiApi.hasAccessToken()) return emptyMap()
        val result = HashMap<Int, BangumiEpisodeCollectionType>()
        val seenEpisodeIds = HashSet<Int>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = try {
                throttled(throttle) {
                    bangumiApi.request {
                        getUserSubjectEpisodeCollection(subjectId, offset = offset, limit = limit)
                    }
                }
            } catch (e: ResponseException) {
                if (e.response.status == HttpStatusCode.NotFound && offset == 0) {
                    return result
                }
                throw e
            }
            val data = page.data.orEmpty()
            if (data.any { !seenEpisodeIds.add(it.episode.id) }) {
                throw RepositoryRequestError(
                    "Bangumi 看過資料含有重複 episode：subject=$subjectId，offset=$offset",
                )
            }
            data.forEach { result[it.episode.id] = it.type }
            if (data.isEmpty()) {
                if (result.size < page.total) {
                    throw RepositoryRequestError(
                        "Bangumi 看過資料不完整：subject=$subjectId，取得 ${result.size}/${page.total}",
                    )
                }
                break
            }
            if (result.size >= page.total) break
            if (data.size < limit) {
                throw RepositoryRequestError(
                    "Bangumi 看過資料分頁提前結束：subject=$subjectId，取得 ${result.size}/${page.total}",
                )
            }
            val nextOffset = offset + data.size
            if (nextOffset <= offset) throw RepositoryRequestError("Bangumi 看過資料 offset 未前進")
            offset = nextOffset
        }
        return result
    }

    private companion object {
        const val MAX_SYNC_SUBJECT_WORKERS = 3
    }
}

private class BangumiSyncRequestThrottle(
    private val intervalMillis: Long = 200L,
) {
    private val mutex = Mutex()
    private var nextStartAt = 0L

    suspend fun <T> run(block: suspend () -> T): T {
        val waitMillis = mutex.withLock {
            val now = currentTimeMillis()
            val startAt = maxOf(now, nextStartAt)
            nextStartAt = startAt + intervalMillis
            startAt - now
        }
        if (waitMillis > 0) delay(waitMillis)
        return block()
    }
}

private fun BangumiSubject.toWynimeSubjectCollection(
    collection: BangumiUserSubjectCollection?,
    episodes: List<BangumiEpisode>,
    episodeCollections: Map<Int, BangumiEpisodeCollectionType>,
): SubjectCollectionDto {
    return SubjectCollectionDto(
        id = id.toLong(),
        type = com.wynime.models.SubjectTypeDto.ANIME,
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        airDate = date.orEmpty(),
        aliases = emptyList(),
        favorite = this.collection.toWynimeFavourite(),
        tags = tags.map { TagDto(it.name, it.count) },
        metaTags = emptyList(),
        scoreDetails = rating.count.toScoreDetails(),
        selfRating = collection.toWynimeSelfRatingInfo(),
        episodes = episodes.map { episode ->
            episode.toWynimeEpisodeCollection(id, episodeCollections[episode.id])
        },
        relations = SubjectRelationsDto(
            subjectId = id.toLong(),
            seriesMainSubjectIds = emptyList(),
            seriesMainSubjectNames = emptyList(),
            sequelSubjects = emptyList(),
            sequelSubjectNames = emptyList(),
        ),
        imageLarge = images.large,
        imageThumb = images.grid.ifBlank { images.common },
        score = rating.score.toString(),
        rank = rating.rank,
        collectionType = collection?.type?.toWynimeCollectionType(),
        airingInfo = SubjectAiringInfoDto(begin = date),
        updatedAt = collection?.updatedAt?.toString(),
    )
}

private fun BangumiCollection.toWynimeFavourite() = FavouriteDto(
    wish = wish,
    done = collect,
    doing = doing,
    onHold = onHold,
    dropped = dropped,
)

private fun BangumiCount.toScoreDetails(): Map<String, Int> = mapOf(
    "1" to (_1 ?: 0),
    "2" to (_2 ?: 0),
    "3" to (_3 ?: 0),
    "4" to (_4 ?: 0),
    "5" to (_5 ?: 0),
    "6" to (_6 ?: 0),
    "7" to (_7 ?: 0),
    "8" to (_8 ?: 0),
    "9" to (_9 ?: 0),
    "10" to (_10 ?: 0),
)

private fun BangumiUserSubjectCollection?.toWynimeSelfRatingInfo() = SelfRatingInfoDto(
    score = this?.rate ?: 0,
    tags = this?.tags.orEmpty(),
    isPrivate = this?.private ?: false,
    comment = this?.comment,
)

private fun BangumiEpisode.toWynimeEpisodeCollection(
    subjectId: Int,
    collectionType: BangumiEpisodeCollectionType?,
) = EpisodeCollectionDto(
    episodeId = id.toLong(),
    subjectId = subjectId.toLong(),
    sort = sort.toString(),
    type = type.toWynimeEpisodeType(),
    name = name,
    nameCn = nameCn,
    description = desc,
    ep = ep?.toString(),
    airdate = airdate.takeIf(String::isNotBlank),
    disc = disc,
    duration = duration.takeIf(String::isNotBlank),
    imageMedium = null,
    imageLarge = null,
    collectionType = collectionType.toWynimeEpisodeCollectionType(),
)

private fun Int.toWynimeEpisodeType() = when (this) {
    0 -> EpisodeTypeDto.MAIN
    1 -> EpisodeTypeDto.SPECIAL
    2 -> EpisodeTypeDto.OP
    3 -> EpisodeTypeDto.ED
    4 -> EpisodeTypeDto.TRAILER
    5 -> EpisodeTypeDto.MAD
    else -> EpisodeTypeDto.OTHER
}

private fun BangumiEpisodeCollectionType?.toWynimeEpisodeCollectionType() =
    takeIf { it == BangumiEpisodeCollectionType.WATCHED }?.let { EpisodeCollectionTypeDto.DONE }

private fun CollectionTypeDto.toBangumiCollectionType() = when (this) {
    CollectionTypeDto.WISH -> BangumiSubjectCollectionType.Wish
    CollectionTypeDto.DONE -> BangumiSubjectCollectionType.Done
    CollectionTypeDto.DOING -> BangumiSubjectCollectionType.Doing
    CollectionTypeDto.ON_HOLD -> BangumiSubjectCollectionType.OnHold
    CollectionTypeDto.DROPPED -> BangumiSubjectCollectionType.Dropped
}

private fun BangumiSubjectCollectionType.toWynimeCollectionType() = when (this) {
    BangumiSubjectCollectionType.Wish -> CollectionTypeDto.WISH
    BangumiSubjectCollectionType.Done -> CollectionTypeDto.DONE
    BangumiSubjectCollectionType.Doing -> CollectionTypeDto.DOING
    BangumiSubjectCollectionType.OnHold -> CollectionTypeDto.ON_HOLD
    BangumiSubjectCollectionType.Dropped -> CollectionTypeDto.DROPPED
}

private fun BangumiRelatedCharacter.toRelatedCharacterInfo(
    index: Int,
    withActors: Boolean,
) = RelatedCharacterInfo(
    index = index,
    character = CharacterInfo(
        id = id,
        name = name,
        nameCn = name,
        actors = if (withActors) actors.orEmpty().map(BangumiPerson::toPersonInfo) else emptyList(),
        imageMedium = images?.medium.orEmpty(),
        imageLarge = images?.large.orEmpty(),
    ),
    role = relation.toCharacterRole(),
)

private fun BangumiRelatedPerson.toRelatedPersonInfo(index: Int) = RelatedPersonInfo(
    index = index,
    personInfo = PersonInfo(
        id = id,
        name = name,
        type = PersonType.fromId(type.value),
        careers = career.map(BangumiPersonCareer::toPersonCareer),
        imageLarge = images?.large.orEmpty(),
        imageMedium = images?.medium.orEmpty(),
        summary = "",
        locked = null,
        nameCn = "",
    ),
    position = PersonPosition.findByName(relation),
)

private fun BangumiPerson.toPersonInfo() = PersonInfo(
    id = id,
    name = name,
    type = PersonType.fromId(type.value),
    careers = career.map(BangumiPersonCareer::toPersonCareer),
    imageLarge = images?.large.orEmpty(),
    imageMedium = images?.medium.orEmpty(),
    summary = shortSummary,
    locked = locked,
    nameCn = "",
)

private fun BangumiPersonCareer.toPersonCareer() = when (this) {
    BangumiPersonCareer.PRODUCER -> PersonCareer.PRODUCER
    BangumiPersonCareer.MANGAKA -> PersonCareer.MANGAKA
    BangumiPersonCareer.ARTIST -> PersonCareer.ARTIST
    BangumiPersonCareer.SEIYU -> PersonCareer.SEIYU
    BangumiPersonCareer.WRITER -> PersonCareer.WRITER
    BangumiPersonCareer.ILLUSTRATOR -> PersonCareer.ILLUSTRATOR
    BangumiPersonCareer.ACTOR -> PersonCareer.ACTOR
}

private fun String.toCharacterRole() = when (lowercase()) {
    "主角", "main", "protagonist" -> CharacterRole.MAIN
    "客串", "guest" -> CharacterRole.GUEST
    else -> CharacterRole.SUPPORTING
}

private fun BangumiSubjectRelation.toWynimeRecommendation() = SubjectRecommendationDto(
    subjectName = name,
    subjectNameCn = nameCn,
    imageUrl = images?.large.orEmpty(),
    desc1 = relation,
    desc2 = "",
    subjectId = id.toLong(),
    uri = null,
)

private typealias BangumiSubjectRelation = BangumiV0SubjectRelation
