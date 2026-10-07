package com.wynime.datasources.ikaros.models

import kotlinx.serialization.Serializable

@Serializable
data class IkarosEpisodeRecord(
    val episode: IkarosEpisodeMeta,
    val resources: List<IkarosEpisodeResource>,
)