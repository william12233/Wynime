package com.wynime.app.data.network

import kotlinx.datetime.LocalDate

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
