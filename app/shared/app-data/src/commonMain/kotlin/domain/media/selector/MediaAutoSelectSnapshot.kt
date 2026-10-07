package com.wynime.app.domain.media.selector

import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind

data class MediaSourceSelectionSnapshot(
    val mediaSourceId: String,
    val kind: MediaSourceKind,
    val state: MediaSourceFetchState,
    val results: List<Media>,
)

data class MediaAutoSelectSnapshot(
    val sources: List<MediaSourceSelectionSnapshot>,
    val candidates: List<MaybeExcludedMedia.Included>,
    val preferred: List<MaybeExcludedMedia.Included>,
    val preference: MediaPreference,
    val settings: MediaSelectorSettings,
    val context: MediaSelectorContext,
) {
    internal val availableAlliances get() = candidates.map { it.result.properties.alliance }.distinct().sortedBy { it }
}
