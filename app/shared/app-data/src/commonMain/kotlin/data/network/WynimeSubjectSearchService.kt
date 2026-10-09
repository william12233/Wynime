package com.wynime.app.data.network

import kotlinx.coroutines.CancellationException
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
        val sortValue = when (sort) {
            SearchSort.MATCH -> BangumiSearchSubjectsRequest.Sort.MATCH
            SearchSort.RANK -> BangumiSearchSubjectsRequest.Sort.RANK
            SearchSort.COLLECTION -> BangumiSearchSubjectsRequest.Sort.HEAT
            SearchSort.DATE -> BangumiSearchSubjectsRequest.Sort.MATCH
        }
        val candidates = searchKeywordVariants(keyword)
        var lastFailure: Throwable? = null
        for ((index, candidate) in candidates.withIndex()) {
            val result = try {
                bangumiApi.request {
                    searchSubjects(
                        bangumiSearchSubjectsRequest = BangumiSearchSubjectsRequest(
                            keyword = candidate,
                            sort = sortValue,
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error is Error && error !is LinkageError) throw error
                lastFailure = error
                if (index == candidates.lastIndex) throw error
                continue
            }
            val matches = result.data.orEmpty()
            if (matches.isNotEmpty() || index == candidates.lastIndex) {
                return@withContext matches.map { search -> search.toBatchSubjectDetails() }
            }
        }
        lastFailure?.let { throw it }
        emptyList()
    }

    companion object {
        fun sanitizeKeyword(keyword: String): String {
            return simplifyChineseOrOriginal(sanitizeSearchPunctuation(keyword))
        }

        fun searchKeywordVariants(keyword: String): List<String> {
            val sanitized = sanitizeSearchPunctuation(keyword)
            return linkedSetOf(sanitized, simplifyChineseOrOriginal(sanitized))
                .filter(String::isNotBlank)
        }

        private fun sanitizeSearchPunctuation(keyword: String): String = buildString(keyword.length) {
            for (c in keyword) {
                if (MediaListFilters.charsToDeleteForSearch.contains(c.code)) {
                    append(' ')
                } else {
                    append(c)
                }
            }
        }.replace(Regex("\\s+"), " ").trim()
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
