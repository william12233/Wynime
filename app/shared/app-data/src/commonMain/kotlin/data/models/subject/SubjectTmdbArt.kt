package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SubjectTmdbArt(

    val backdrops: List<TmdbImage> = emptyList(),

    val posters: Map<String, TmdbImage> = emptyMap(),

    val logos: Map<String, TmdbImage> = emptyMap(),
) {

    val primaryBackdrop: TmdbImage? get() = backdrops.firstOrNull()
}

@Immutable
@Serializable
data class TmdbImage(

    val medium: String,

    val large: String,

    val vector: String? = null,
)
