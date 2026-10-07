package com.wynime.datasources.api.topic

import kotlinx.serialization.Serializable
import com.wynime.datasources.api.SubtitleKind

@Serializable
class TopicDetails(
    val tags: List<String>,
    val chineseTitle: String?,
    val otherTitles: List<String>,
    val episodeRange: EpisodeRange?,
    val resolution: Resolution?,
    val frameRate: FrameRate?,
    val mediaOrigin: MediaOrigin?,
    val subtitleLanguages: List<SubtitleLanguage>,
    val subtitleKind: SubtitleKind?,
)

