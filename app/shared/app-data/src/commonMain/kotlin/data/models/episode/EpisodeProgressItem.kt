package com.wynime.app.data.models.episode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wynime.app.domain.media.cache.EpisodeCacheStatus
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Stable
@Deprecated("Use EpisodeListItem instead")
class EpisodeProgressItem(
    val episodeId: Int,
    val episodeSort: String,
    val collectionType: UnifiedCollectionType,
    val isOnAir: Boolean?,
    val cacheStatus: EpisodeCacheStatus?,
) {
    var isLoading by mutableStateOf(false)
}
