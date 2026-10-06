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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
import kotlin.coroutines.CoroutineContext

/**
 * Loads subject and collection data directly from the official Bangumi API.
 */
class BangumiSubjectService(
    private val bangumiApi: BangumiApiProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : SubjectService {
    val subjectCountStatsRestarter = FlowRestarter()

    override suspend fun getSubjectCollections(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int,
    ): List<AniSubjectCollection> = withContext(ioDispatcher) {
        val username = bangumiApi.currentUsername() ?: return@withContext emptyList()
        val page = bangumiApi.request {
            getUserCollectionsByUsername(
                username = username,
                subjectType = BangumiSubjectType.Anime,
                type = type,
                limit = limit,
                offset = offset,
            )
        }
        page.data.orEmpty().mapNotNull { collection ->
            buildSubjectCollection(collection.subjectId, collection, username)
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
    ): AniSubjectCollection? {
        val subject = try {
            bangumiApi.request { getSubjectById(subjectId) }
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) return null
            throw e
        }
        val userCollection = collection ?: loadUserCollection(subjectId, username)
        val episodes = loadEpisodes(subjectId)
        val episodeCollections = loadEpisodeCollections(subjectId)

        return subject.toAniSubjectCollection(
            collection = userCollection,
            episodes = episodes,
            episodeCollections = episodeCollections,
        )
    }

    private suspend fun loadUserCollection(
        subjectId: Int,
        username: String? = null,
    ): BangumiUserSubjectCollection? {
        val currentUsername = username ?: bangumiApi.currentUsername() ?: return null
        return try {
            bangumiApi.request { getUserCollection(username = currentUsername, subjectId = subjectId) }
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

    private suspend fun loadEpisodes(subjectId: Int): List<BangumiEpisode> {
        val result = ArrayList<BangumiEpisode>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = bangumiApi.request {
                getEpisodes(subjectId = subjectId, limit = limit, offset = offset)
            }
            val data = page.data.orEmpty()
            result += data
            if (data.isEmpty() || data.size < limit || result.size >= (page.total ?: result.size)) break
            offset += data.size
        }
        return result
    }

    private suspend fun loadEpisodeCollections(subjectId: Int): Map<Int, BangumiEpisodeCollectionType> {
        if (!bangumiApi.hasAccessToken()) return emptyMap()
        val result = HashMap<Int, BangumiEpisodeCollectionType>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = try {
                bangumiApi.request {
                    getUserSubjectEpisodeCollection(subjectId, offset = offset, limit = limit)
                }
            } catch (e: ResponseException) {
                if (e.response.status == HttpStatusCode.NotFound ||
                    e.response.status == HttpStatusCode.Unauthorized ||
                    e.response.status == HttpStatusCode.Forbidden
                ) {
                    return result
                }
                throw e
            }
            val data = page.data.orEmpty()
            data.forEach { result[it.episode.id] = it.type }
            if (data.isEmpty() || data.size < limit || result.size >= page.total) break
            offset += data.size
        }
        return result
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
