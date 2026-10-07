package com.wynime.app.data.models.trending

import com.wynime.models.TrendingSubjectDto
import kotlinx.datetime.LocalDate

data class TrendsInfo(
    val subjects: List<TrendingSubjectInfo>,
    val total: Int = subjects.size,
)

data class TrendingSubjectInfo(
    val bangumiId: Int,
    val nameCn: String,
    val imageLarge: String,
    val name: String = nameCn,
    val nsfw: Boolean = false,
    val score: Double = 0.0,
    val scoreCount: Int = 0,
    val rank: Int = 0,
    val tags: List<String> = emptyList(),
    val airDate: LocalDate? = null,
    val trendingCount: Int = 0,
)
