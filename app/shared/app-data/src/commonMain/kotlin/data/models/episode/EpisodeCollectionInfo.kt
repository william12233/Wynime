package com.wynime.app.data.models.episode

import androidx.compose.runtime.Immutable
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Immutable
data class EpisodeCollectionInfo(
    val episodeInfo: EpisodeInfo,
    val collectionType: UnifiedCollectionType,
) {
    val episodeId: Int get() = episodeInfo.episodeId
}