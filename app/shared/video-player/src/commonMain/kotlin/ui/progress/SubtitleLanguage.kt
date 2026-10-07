package com.wynime.app.videoplayer.ui.progress

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import org.openani.mediamp.metadata.SubtitleTrack

@Immutable
class SubtitlePresentation(
    val subtitleTrack: SubtitleTrack,
    val displayName: String,
)

@Stable
val SubtitleTrack.subtitleLanguage: String
    get() = language ?: labels.firstOrNull()?.value ?: internalId

