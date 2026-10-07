package com.wynime.app.data.models.subject

import kotlinx.datetime.TimeZone
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.PackedDate

data class LightSubjectAndEpisodes(
    val subject: LightSubjectInfo,
    val episodes: List<LightEpisodeInfo>,
) {
    val subjectId get() = subject.subjectId
}

data class LightSubjectInfo(
    val subjectId: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
)

val LightSubjectInfo.displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name
val LightSubjectInfo.nameOrNameCn get() = name.ifBlank { nameCn }

data class LightEpisodeInfo(
    val episodeId: Int,
    val name: String,
    val nameCn: String,
    val airDate: PackedDate,
    val timezone: TimeZone,
    val sort: EpisodeSort,
    val ep: EpisodeSort?,
)

val LightEpisodeInfo.displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name
val LightEpisodeInfo.nameOrNameCn get() = name.ifBlank { nameCn }
