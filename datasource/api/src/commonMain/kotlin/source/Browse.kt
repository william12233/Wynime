package com.wynime.datasources.api.source

import kotlinx.serialization.Serializable
import com.wynime.datasources.api.EpisodeSort

@Serializable
data class BrowseSubject(

    val name: String,

    val url: String,
)

@Serializable
data class BrowseChannel(

    val name: String?,

    val label: String? = name,

    val episodes: List<BrowseEpisode>,
)

@Serializable
data class BrowseEpisode(

    val name: String,

    val url: String,

    val episodeSort: EpisodeSort? = null,
)
