package com.wynime.app.data.models.episode

import com.wynime.app.domain.media.cache.EpisodeCacheStatus
import com.wynime.datasources.api.topic.UnifiedCollectionType

class EpisodeProgressInfo(
    val episode: EpisodeInfo,
    val collectionType: UnifiedCollectionType,
    val cacheStatus: EpisodeCacheStatus,
)

fun List<EpisodeProgressInfo>.findCacheStatus(episodeId: Int): EpisodeCacheStatus? {
    return find { it.episode.episodeId == episodeId }?.cacheStatus
}
