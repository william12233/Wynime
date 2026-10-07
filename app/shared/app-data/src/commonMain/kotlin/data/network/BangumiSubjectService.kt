/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

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
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.PersonCareer
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.session.checkAccessAniApiNow
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.client.models.AniSubjectAiringInfo
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRecommendation
import me.him188.ani.client.models.AniSubjectRelations
import me.him188.ani.client.models.AniTag
import me.him188.ani.client.models.AniUpdateSubjectCollectionRequest
import me.him188.ani.datasources.bangumi.models.BangumiCollection
import me.him188.ani.datasources.bangumi.models.BangumiCount
import me.him188.ani.datasources.bangumi.models.BangumiEpisode
import me.him188.ani.datasources.bangumi.models.BangumiEpisodeCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiPerson
import me.him188.ani.datasources.bangumi.models.BangumiPersonCareer
import me.him188.ani.datasources.bangumi.models.BangumiRelatedCharacter
import me.him188.ani.datasources.bangumi.models.BangumiRelatedPerson
import me.him188.ani.datasources.bangumi.models.BangumiSubject
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollection
import me.him188.ani.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import me.him188.ani.datasources.bangumi.models.BangumiV0SubjectRelation
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext

/**
 * Loads subject and collection data directly from the official Bangumi API.
 */
class BangumiSubjectService(
    private val bangumiApi: BangumiApiProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val aniApi: AniApiProvider? = null,
    private val sessionManager: SessionStateProvider? = null,
) : SubjectService {
    private val logger = logger<BangumiSubjectService>()
    val subjectCountStatsRestarter = FlowRestarter()
    private val syncRequestThrottle = BangumiSyncRequestThrottle()

    override suspend fun getSubjectCollections(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int,
    ): List<AniSubjectCollection> = getSubjectCollectionsPage(type, offset, limit).also { page ->
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

    override suspend fun getSubjectCollection(subjectId: Int): AniSubjectCollection? =
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

    override fun subjectCollectionById(subjectId: Int): Flow<AniSubjectCollection?> = flow {
        emit(getSubjectCollection(subjectId))
    }.flowOn(ioDispatcher)

    override suspend fun patchSubjectCollection(
        subjectId: Int,
        payload: AniUpdateSubjectCollectionRequest,
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
            val aniApi = aniApi ?: throw RepositoryRequestError("Ani API is required to delete a collection")
            val sessionManager = sessionManager
                ?: throw RepositoryRequestError("Ani session is required to delete a collection")
            sessionManager.checkAccessAniApiNow()
            aniApi.subjectApi {
                deleteSubjectCollection(subjectId.toLong())
                Unit
            }
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
    ): List<AniSubjectRecommendation> = withContext(ioDispatcher) {
        bangumiApi.request { getRelatedSubjectsBySubjectId(subjectId) }
            .take(limit)
            .map { relation ->
                AniSubjectRecommendation(
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
    ): AniSubjectCollection? {
        val subject = try {
            requestSubject(throttle, subjectId)
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) return null
            throw e
        }
        val userCollection = collection ?: loadUserCollection(subjectId, username, throttle)
        val episodes = loadEpisodes(subjectId, throttle)
        val episodeCollections = loadEpisodeCollections(subjectId, throttle)

        return subject.toAniSubjectCollection(
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

private fun BangumiSubject.toAniSubjectCollection(
    collection: BangumiUserSubjectCollection?,
    episodes: List<BangumiEpisode>,
    episodeCollections: Map<Int, BangumiEpisodeCollectionType>,
): AniSubjectCollection {
    return AniSubjectCollection(
        id = id.toLong(),
        type = me.him188.ani.client.models.AniSubjectType.ANIME,
        name = name,
        nameCn = nameCn,
        summary = summary,
        nsfw = nsfw,
        airDate = date.orEmpty(),
        aliases = emptyList(),
        favorite = this.collection.toAniFavourite(),
        tags = tags.map { AniTag(it.name, it.count) },
        metaTags = emptyList(),
        scoreDetails = rating.count.toScoreDetails(),
        selfRating = collection.toAniSelfRatingInfo(),
        episodes = episodes.map { episode ->
            episode.toAniEpisodeCollection(id, episodeCollections[episode.id])
        },
        relations = AniSubjectRelations(
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
        collectionType = collection?.type?.toAniCollectionType(),
        airingInfo = AniSubjectAiringInfo(begin = date),
        updatedAt = collection?.updatedAt?.toString(),
    )
}

private fun BangumiCollection.toAniFavourite() = AniFavourite(
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

private fun BangumiUserSubjectCollection?.toAniSelfRatingInfo() = AniSelfRatingInfo(
    score = this?.rate ?: 0,
    tags = this?.tags.orEmpty(),
    isPrivate = this?.private ?: false,
    comment = this?.comment,
)

private fun BangumiEpisode.toAniEpisodeCollection(
    subjectId: Int,
    collectionType: BangumiEpisodeCollectionType?,
) = AniEpisodeCollection(
    episodeId = id.toLong(),
    subjectId = subjectId.toLong(),
    sort = sort.toString(),
    type = type.toAniEpisodeType(),
    name = name,
    nameCn = nameCn,
    description = desc,
    ep = ep?.toString(),
    airdate = airdate.takeIf(String::isNotBlank),
    disc = disc,
    duration = duration.takeIf(String::isNotBlank),
    imageMedium = null,
    imageLarge = null,
    collectionType = collectionType.toAniEpisodeCollectionType(),
)

private fun Int.toAniEpisodeType() = when (this) {
    0 -> AniEpisodeType.MAIN
    1 -> AniEpisodeType.SPECIAL
    2 -> AniEpisodeType.OP
    3 -> AniEpisodeType.ED
    4 -> AniEpisodeType.TRAILER
    5 -> AniEpisodeType.MAD
    else -> AniEpisodeType.OTHER
}

private fun BangumiEpisodeCollectionType?.toAniEpisodeCollectionType() =
    takeIf { it == BangumiEpisodeCollectionType.WATCHED }?.let { AniEpisodeCollectionType.DONE }

private fun AniCollectionType.toBangumiCollectionType() = when (this) {
    AniCollectionType.WISH -> BangumiSubjectCollectionType.Wish
    AniCollectionType.DONE -> BangumiSubjectCollectionType.Done
    AniCollectionType.DOING -> BangumiSubjectCollectionType.Doing
    AniCollectionType.ON_HOLD -> BangumiSubjectCollectionType.OnHold
    AniCollectionType.DROPPED -> BangumiSubjectCollectionType.Dropped
}

private fun BangumiSubjectCollectionType.toAniCollectionType() = when (this) {
    BangumiSubjectCollectionType.Wish -> AniCollectionType.WISH
    BangumiSubjectCollectionType.Done -> AniCollectionType.DONE
    BangumiSubjectCollectionType.Doing -> AniCollectionType.DOING
    BangumiSubjectCollectionType.OnHold -> AniCollectionType.ON_HOLD
    BangumiSubjectCollectionType.Dropped -> AniCollectionType.DROPPED
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

private fun BangumiSubjectRelation.toAniRecommendation() = AniSubjectRecommendation(
    subjectName = name,
    subjectNameCn = nameCn,
    imageUrl = images?.large.orEmpty(),
    desc1 = relation,
    desc2 = "",
    subjectId = id.toLong(),
    uri = null,
)

private typealias BangumiSubjectRelation = BangumiV0SubjectRelation
