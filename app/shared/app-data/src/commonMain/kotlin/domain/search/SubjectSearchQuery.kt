package com.wynime.app.domain.search

import com.wynime.app.data.models.schedule.AnimeSeason

data class SubjectSearchQuery(
    val keywords: String,
    val type: SubjectType = SubjectType.ANIME,

    val tags: List<String>? = null,

    val year: Int? = null,

    val season: AnimeSeason? = null,
    val rating: RatingRange? = null,

    val nsfw: Boolean? = null,
    val sort: SearchSort = SearchSort.MATCH,
) {
    init {

        require(season == null || year != null) { "season 从属于 year, 不能单独设置" }
    }

    fun normalized(): SubjectSearchQuery {
        return copy(keywords = keywords.trim())
    }

    fun hasFilters(): Boolean {
        return tags != null || year != null || season != null || rating != null || nsfw != null ||
                sort != SearchSort.MATCH
    }

    fun hasSearchRequest(): Boolean {
        return keywords.isNotEmpty() || hasFilters()
    }
}

fun SubjectSearchQuery.withYearFilter(year: Int?): SubjectSearchQuery {
    return if (year == null) {
        copy(year = null, season = null)
    } else {
        copy(year = year)
    }
}

enum class SearchSort {
    MATCH,

    RANK,

    COLLECTION,

    DATE,
}

data class RatingRange(
    val min: Int?,
    val max: Int?,
)

enum class SubjectType {
    ANIME,

}
