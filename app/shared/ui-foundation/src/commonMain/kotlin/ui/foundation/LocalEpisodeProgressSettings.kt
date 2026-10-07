package com.wynime.app.ui.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.wynime.app.data.models.preference.EpisodeProgressSettings

val LocalEpisodeProgressSettings: ProvidableCompositionLocal<EpisodeProgressSettings> =
    compositionLocalOf { EpisodeProgressSettings.Default }
