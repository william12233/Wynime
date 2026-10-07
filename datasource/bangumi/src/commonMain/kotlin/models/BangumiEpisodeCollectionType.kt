package com.wynime.datasources.bangumi.models

import kotlinx.serialization.Serializable

@Serializable(with = BangumiEpisodeCollectionTypeAsInt::class)
enum class BangumiEpisodeCollectionType(val value: Int) {
    NOT_COLLECTED(0),
    WATCHLIST(1),
    WATCHED(2),
    DISCARDED(3);

    override fun toString(): String = value.toString()
}

private object BangumiEpisodeCollectionTypeAsInt : EnumValueSerializer<BangumiEpisodeCollectionType>(
    "BangumiEpisodeCollectionType", BangumiEpisodeCollectionType.entries, { it.value },
)
