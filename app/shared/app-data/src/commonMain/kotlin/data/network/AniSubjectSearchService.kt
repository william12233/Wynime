/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.bangumi.models.BangumiSearchSubjectsRequest
import me.him188.ani.datasources.bangumi.models.BangumiSearchSubjectsRequestFilter
import me.him188.ani.datasources.bangumi.models.BangumiSubjectType
import me.him188.ani.datasources.bangumi.models.BangumiTag
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext


class AniSubjectSearchService(
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

    private fun me.him188.ani.datasources.bangumi.models.BangumiSearchSubjects200ResponseDataInner
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
