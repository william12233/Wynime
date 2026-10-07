package com.wynime.app.data.models.player

import kotlinx.serialization.Serializable

@Serializable
data class EpisodeHistory(
    val episodeId: Int,
    val positionMillis: Long,
    val subjectId: Int? = null,
    val episodeSort: Float? = null,
    val subjectName: String? = null,
    val subjectImageUrl: String? = null,
    val episodeName: String? = null,
    val durationMillis: Long? = null,
    val updatedAtMillis: Long = 0,
    val deletedAtMillis: Long? = null,
    val serverRevision: Long = 0,
    val isDirty: Boolean = true,
) {
    val isDeleted: Boolean get() = deletedAtMillis != null

    val versionMillis: Long get() = maxOf(updatedAtMillis, deletedAtMillis ?: 0L)
}

val EpisodeHistory.playProgress: Float?
    get() = durationMillis?.takeIf { it > 0L }?.let { (positionMillis.toFloat() / it).coerceIn(0f, 1f) }

fun List<EpisodeHistory>.playProgressByEpisodeId(): Map<Int, Float> = buildMap {
    for (history in this@playProgressByEpisodeId) {
        val progress = history.playProgress ?: continue
        if (progress > 0f) put(history.episodeId, progress)
    }
}
