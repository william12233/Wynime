package com.wynime.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.models.subject.RatingCounts
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.domain.mediasource.MediaListFilters
import com.wynime.app.domain.search.SearchSort
import com.wynime.app.domain.search.SubjectType
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.bangumi.models.BangumiSearchSubjectsRequest
import com.wynime.datasources.bangumi.models.BangumiSearchSubjectsRequestFilter
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiTag
import com.wynime.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

class WynimeSubjectSearchService(
    private val bangumiApi: BangumiApiProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    suspend fun searchSubjects(
        keyword: String,
        offset: Int? = null,
        limit: Int? = null,

        sort: SearchSort = SearchSort.MATCH,
        filters: SubjectSearchFilters? = null,
        fields: List<SubjectSearchField>? = null,
    ): List<BatchSubjectDetails> = withContext(ioDispatcher) {
        val result = bangumiApi.request {
            searchSubjects(
                bangumiSearchSubjectsRequest = BangumiSearchSubjectsRequest(
                    keyword = keyword,
                    sort = when (sort) {
                        SearchSort.MATCH -> BangumiSearchSubjectsRequest.Sort.MATCH
                        SearchSort.RANK -> BangumiSearchSubjectsRequest.Sort.RANK
                        SearchSort.COLLECTION -> BangumiSearchSubjectsRequest.Sort.HEAT
                        SearchSort.DATE -> BangumiSearchSubjectsRequest.Sort.MATCH
                    },
                    filter = BangumiSearchSubjectsRequestFilter(
                        type = listOf(BangumiSubjectType.Anime),
                        tag = filters?.tags,
                        airDate = filters?.airDates,
                        rating = filters?.ratings,
                        rank = filters?.ranks,
                        nsfw = filters?.nsfw,
                    ),
                ),
                offset = offset,
                limit = limit,
            )
        }

        result.data.orEmpty().map { search -> search.toBatchSubjectDetails() }
    }

    companion object {
        fun sanitizeKeyword(keyword: String): String {
            return buildString(keyword.length) {
                for (c in keyword) {
                    if (MediaListFilters.charsToDeleteForSearch.contains(c.code)) {
                        append(' ')
                    } else {
                        append(c)
                    }
                }
            }
        }
    }

    private fun com.wynime.datasources.bangumi.models.BangumiSearchSubjects200ResponseDataInner
        .toBatchSubjectDetails(): BatchSubjectDetails {
        return BatchSubjectDetails(
            subjectInfo = SubjectInfo(
                subjectId = this.id.toInt(),
                subjectType = SubjectType.ANIME,
                name = this.name,
                nameCn = this.nameCn,
                summary = this.summary,
                nsfw = false,
                imageLarge = this.image,
                totalEpisodes = 0,
                airDate = this.date?.let(PackedDate::parseFromDate) ?: PackedDate.Invalid,
                tags = this.tags.map(BangumiTag::toTag),
                aliases = emptyList(),
                ratingInfo = RatingInfo(
                    rank = this.rank ?: 0,
                    total = 0,
                    count = RatingCounts.Zero,
                    score = this.score?.toString().orEmpty(),
                ),
                collectionStats = SubjectCollectionStats.Zero,
                completeDate = PackedDate.Invalid,

                ),
            mainEpisodeCount = 0,
            lightSubjectRelations = LightSubjectRelations(
                lightRelatedPersonInfoList = emptyList(),
                lightRelatedCharacterInfoList = emptyList(),
            ),
        )
    }
}

private fun BangumiTag.toTag() = Tag(name, count)
