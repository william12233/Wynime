package com.wynime.app.data.network

import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.episode.toEpisodeCollectionInfo
import com.wynime.app.data.repository.subject.toEntity1
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.EpisodeType.*
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.paging.Paged
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.bangumi.models.BangumiEpType
import com.wynime.datasources.bangumi.models.BangumiEpisode
import com.wynime.datasources.bangumi.models.BangumiEpisodeDetail
import com.wynime.datasources.bangumi.models.BangumiPatchUserSubjectEpisodeCollectionRequest
import com.wynime.datasources.bangumi.models.BangumiUserEpisodeCollection
import com.wynime.datasources.bangumi.processing.toCollectionType
import com.wynime.datasources.bangumi.processing.toEpisodeCollectionType
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.platform.currentTimeMillis
import com.wynime.utils.serialization.BigNum
import kotlin.coroutines.CoroutineContext

sealed interface EpisodeService {

    suspend fun getEpisodeCollectionInfosPaged(
        subjectId: Int,
        offset: Int? = 0,
        limit: Int? = 100,
        episodeType: BangumiEpType? = null,
    ): Paged<EpisodeCollectionInfo>

    suspend fun getEpisodeCollectionById(subjectId: Int, episodeId: Int): EpisodeCollectionInfo?

    suspend fun setEpisodeCollection(
        subjectId: Int,
        episodeId: List<Int>,
        type: UnifiedCollectionType,
    ): Boolean
}

class EpisodeServiceImpl(
    private val bangumiApi: BangumiApiProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : EpisodeService {

    override suspend fun getEpisodeCollectionInfosPaged(
        subjectId: Int,
        offset: Int?,
        limit: Int?,
        episodeType: BangumiEpType?,
    ): Paged<EpisodeCollectionInfo> {
        return withContext(ioDispatcher) {
            val requestedOffset = offset ?: 0
            val requestedLimit = limit?.takeIf { it > 0 } ?: 100
            val episodePage = bangumiApi.request {
                getEpisodes(
                    subjectId = subjectId,
                    type = episodeType,
                    limit = requestedLimit,
                    offset = requestedOffset,
                )
            }
            val episodes = episodePage.data.orEmpty()
            val responseOffset = episodePage.offset ?: requestedOffset
            if (responseOffset != requestedOffset) {
                throw RepositoryRequestError(
                    "Bangumi 集數回傳 offset 不一致：要求 $requestedOffset，收到 $responseOffset",
                )
            }
            val total = episodePage.total
            if (total != null && (total < 0 || responseOffset > total || responseOffset + episodes.size > total)) {
                throw RepositoryRequestError(
                    "Bangumi 集數資料超過 total：offset=$responseOffset，取得 ${episodes.size}，total=$total",
                )
            }
            if (episodes.isEmpty() && total != null && responseOffset < total) {
                throw RepositoryRequestError(
                    "Bangumi 集數資料不完整：offset=$responseOffset，total=$total",
                )
            }
            if (total != null && responseOffset + episodes.size < total && episodes.size < requestedLimit) {
                throw RepositoryRequestError(
                    "Bangumi 集數分頁提前結束：offset=$responseOffset，取得 ${responseOffset + episodes.size}/$total",
                )
            }
            val collections = if (bangumiApi.hasAccessToken()) {
                loadUserEpisodeCollections(subjectId, episodeType)
            } else {
                emptyMap()
            }
            val hasMore = if (total != null) {
                responseOffset + episodes.size < total
            } else {
                episodes.size >= requestedLimit
            }
            Paged(
                total = total,
                hasMore = hasMore,
                page = episodes.map { episode ->
                    collections[episode.id]?.toEpisodeCollectionInfo()
                        ?: episode.toEpisodeInfo().createNotCollected()
                },
            )
        }
    }

    override suspend fun getEpisodeCollectionById(subjectId: Int, episodeId: Int): EpisodeCollectionInfo? =
        withContext(ioDispatcher) {
            val episode = try {
                val episode = bangumiApi.request { getEpisodeById(episodeId) }
                episode
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) {
                    return@withContext null
                }
                throw e
            }
            if (!bangumiApi.hasAccessToken()) return@withContext episode.toEpisodeInfo().createNotCollected()
            val collection = try {
                bangumiApi.request { getUserEpisodeCollection(episodeId) }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) null else throw e
            }
            collection?.toEpisodeCollectionInfo()
                ?: episode.toEpisodeInfo().createNotCollected()
        }

    private suspend fun loadUserEpisodeCollections(
        subjectId: Int,
        episodeType: BangumiEpType?,
    ): Map<Int, BangumiUserEpisodeCollection> {
        val result = HashMap<Int, BangumiUserEpisodeCollection>()
        val seenEpisodeIds = HashSet<Int>()
        val limit = 100
        var offset = 0
        while (true) {
            val page = try {
                bangumiApi.request {
                    getUserSubjectEpisodeCollection(
                        subjectId = subjectId,
                        offset = offset,
                        limit = limit,
                        episodeType = episodeType,
                    )
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound && offset == 0) {
                    return emptyMap()
                }
                throw e
            }
            val data = page.data.orEmpty()
            if (page.offset != offset || page.limit <= 0 || page.total < 0) {
                throw RepositoryRequestError(
                    "Bangumi 看過資料分頁欄位不一致：offset=$offset，回傳 offset=${page.offset} limit=${page.limit} total=${page.total}",
                )
            }
            if (data.any { !seenEpisodeIds.add(it.episode.id) }) {
                throw RepositoryRequestError(
                    "Bangumi 看過資料含有重複 episode：subject=$subjectId，offset=$offset",
                )
            }
            if (offset + data.size > page.total) {
                throw RepositoryRequestError(
                    "Bangumi 看過資料超過 total：subject=$subjectId，取得 ${offset + data.size}/${page.total}",
                )
            }
            result.putAll(data.associateBy { it.episode.id })
            if (offset + data.size >= page.total) break
            if (data.isEmpty() || data.size < limit) {
                throw RepositoryRequestError(
                    "Bangumi 看過資料分頁提前結束：subject=$subjectId，取得 ${offset + data.size}/${page.total}",
                )
            }
            val nextOffset = offset + data.size
            if (nextOffset <= offset) {
                throw RepositoryRequestError("Bangumi 看過資料 offset 未前進")
            }
            offset = nextOffset
        }
        return result
    }

    override suspend fun setEpisodeCollection(
        subjectId: Int,
        episodeId: List<Int>,
        type: UnifiedCollectionType,
    ): Boolean = withContext(ioDispatcher) {
        if (!bangumiApi.hasAccessToken()) {
            return@withContext false
        }
        try {
            bangumiApi.request {
                patchUserSubjectEpisodeCollection(
                    subjectId,
                    BangumiPatchUserSubjectEpisodeCollectionRequest(
                        episodeId = episodeId,
                        type = type.toEpisodeCollectionType(),
                    ),
                )
            }
            true
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return@withContext false
            }
            throw e
        }
    }

    private companion object {
        fun HttpStatusCode.isUnauthorized(): Boolean {
            return this == HttpStatusCode.Unauthorized || this == HttpStatusCode.Forbidden
        }

        fun HttpStatusCode.isServerError(): Boolean {
            return this.value in 500..599
        }
    }
}

private fun EpisodeInfo.createNotCollected(): EpisodeCollectionInfo {
    return EpisodeCollectionInfo(
        episodeInfo = this,
        collectionType = UnifiedCollectionType.NOT_COLLECTED,
    )
}

private fun BangumiUserEpisodeCollection.toEpisodeCollectionInfo() =
    EpisodeCollectionInfo(episode.toEpisodeInfo(), type.toCollectionType())

internal fun BangumiEpisode.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = this.id,
        type = getEpisodeTypeByBangumiCode(this.type),
        name = this.name,
        nameCn = this.nameCn,
        airDate = PackedDate.parseFromDate(this.airdate),
        comment = this.comment,

        desc = this.desc,

        sort = EpisodeSort(this.sort, getEpisodeTypeByBangumiCode(this.type)),
        ep = EpisodeSort(this.ep ?: BigNum.ONE, getEpisodeTypeByBangumiCode(this.type)),

    )
}

internal fun BangumiEpisodeDetail.toEpisodeInfo(): EpisodeInfo {
    return EpisodeInfo(
        episodeId = id,
        type = getEpisodeTypeByBangumiCode(this.type),
        name = name,
        nameCn = nameCn,
        sort = EpisodeSort(this.sort, getEpisodeTypeByBangumiCode(this.type)),
        airDate = PackedDate.parseFromDate(this.airdate),
        comment = comment,

        desc = desc,

        ep = EpisodeSort(this.ep ?: BigNum.ONE, getEpisodeTypeByBangumiCode(this.type)),
    )
}

internal fun EpisodeType.toBangumiEpType(): BangumiEpType {
    return when (this) {
        MainStory -> BangumiEpType.MainStory
        SP -> BangumiEpType.SP
        OP -> BangumiEpType.OP
        ED -> BangumiEpType.ED
        PV -> BangumiEpType.PV
        MAD -> BangumiEpType.MAD
        EpisodeType.OVA -> BangumiEpType.Other
        EpisodeType.OAD -> BangumiEpType.Other
    }
}

internal fun BangumiEpType.toEpisodeType(): EpisodeType? {
    return when (this) {
        BangumiEpType.MainStory -> MainStory
        BangumiEpType.SP -> SP
        BangumiEpType.OP -> OP
        BangumiEpType.ED -> ED
        BangumiEpType.PV -> PV
        BangumiEpType.MAD -> MAD
        BangumiEpType.Other -> null
    }
}

private fun getEpisodeTypeByBangumiCode(code: Int): EpisodeType? {
    return when (code) {
        0 -> MainStory
        1 -> SP
        2 -> OP
        3 -> ED
        4 -> PV
        5 -> MAD
        else -> null
    }
}
