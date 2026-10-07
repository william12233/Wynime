package com.wynime.app.ui.subject.episode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.models.episode.nameOrNameCn
import com.wynime.app.data.models.episode.renderEpisodeEp
import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Immutable
data class EpisodePresentation(
    val episodeId: Int,

    val title: String,

    val originalTitle: String = title,

    val ep: String,

    val sort: String,
    val collectionType: UnifiedCollectionType,

    val isKnownBroadcast: Boolean,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        @Stable
        val Placeholder = EpisodePresentation(
            episodeId = -1,
            title = "placeholder",
            ep = "placeholder",
            sort = "placeholder",
            collectionType = UnifiedCollectionType.WISH,
            isKnownBroadcast = false,
            isPlaceholder = true,
        )
    }
}

fun EpisodeCollectionInfo.toPresentation(
    recurrence: SubjectRecurrence?,
) = EpisodePresentation(
    episodeId = this.episodeInfo.episodeId,
    title = episodeInfo.displayName,
    originalTitle = episodeInfo.nameOrNameCn,
    ep = episodeInfo.renderEpisodeEp(),
    sort = episodeInfo.sort.toString(),
    collectionType = collectionType,
    isKnownBroadcast = episodeInfo.isKnownCompleted(recurrence),
)
