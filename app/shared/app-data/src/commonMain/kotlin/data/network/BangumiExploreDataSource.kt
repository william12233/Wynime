/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.datetime.LocalDate

/**
 * Bangumi exploration data used by the home feeds and the schedule.
 *
 * The interface keeps feed logic independent from the generated HTTP client. It also makes
 * partial response handling and deterministic ranking testable without a network dependency.
 */
interface BangumiExploreDataSource {
    suspend fun getTrendingSubjects(limit: Int, offset: Int): BangumiTrendingPage

    suspend fun getCalendarDays(): List<BangumiCalendarDay>

    suspend fun getEpisodes(subjectId: Int): List<BangumiExploreEpisode>

    suspend fun getCollectionPreferences(): List<BangumiCollectionPreference>
}

data class BangumiTrendingPage(
    val subjects: List<BangumiExploreSubject>,
    val total: Int,
)

data class BangumiCalendarDay(
    val weekdayId: Int,
    val items: List<BangumiCalendarEntry>,
)

data class BangumiExploreSubject(
    val id: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
    val type: Int = 2,
    val nsfw: Boolean = false,
    val score: Double = 0.0,
    val scoreCount: Int = 0,
    val rank: Int = 0,
    val tags: List<String> = emptyList(),
    val airDate: LocalDate? = null,
    val trendingCount: Int = 0,
)

data class BangumiCalendarEntry(
    val id: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
    val type: Int = 2,
    val nsfw: Boolean = false,
    val airDate: LocalDate? = null,
)

data class BangumiExploreEpisode(
    val id: Int,
    val type: Int,
    val name: String,
    val nameCn: String,
    val sort: String,
    val ep: String?,
    val airDate: LocalDate?,
)

data class BangumiCollectionPreference(
    val subjectId: Int,
    val collectionType: Int,
    val tags: List<String>,
    val subjectTags: List<String>,
    val score: Int,
    val nsfw: Boolean,
)
