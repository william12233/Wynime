package com.wynime.app.data.network

data class SubjectSearchFilters(
    val tags: List<String>? = null,
    val airDates: List<String>? = null,
    val ratings: List<String>? = null,
    val ranks: List<String>? = null,
    val nsfw: Boolean? = null,
)

enum class SubjectSearchField {
    NAME,
    SUMMARY,
    IMAGE_LARGE,
    NSFW,
    AIR_DATE,
    SCORE,
    RANK,
    RATING_TOTAL,
    FAVORITE,
    TAGS,
    MAIN_EPISODE_COUNT,
    LIGHT_RELATED_PERSON_INFO,
}
