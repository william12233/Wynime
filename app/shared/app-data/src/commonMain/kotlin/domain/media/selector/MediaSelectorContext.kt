package com.wynime.app.domain.media.selector

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryUnknownException
import com.wynime.app.domain.media.selector.MediaSelectorContext.Companion.Initial
import com.wynime.app.domain.mediasource.MediaSourceTier
import com.wynime.utils.coroutines.retryWithBackoffDelay
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn

data class MediaSelectorContext(

    val subjectFinished: Boolean?,

    val mediaSourcePrecedence: List<String>?,

    val subtitlePreferences: MediaSelectorSubtitlePreferences?,
    val subjectSeriesInfo: SubjectSeriesInfo?,
    val subjectInfo: SubjectInfo?,
    val episodeInfo: EpisodeInfo?,
    val mediaSourceTiers: MediaSelectorSourceTiers?,
) {

    val hasEpisode: Boolean get() = episodeInfo != null && episodeInfo != EpisodeInfo.Empty

    fun allFieldsLoaded() = subjectFinished != null
            && mediaSourcePrecedence != null
            && subtitlePreferences != null
            && subjectSeriesInfo != null
            && subjectInfo != null
            && episodeInfo != null
            && mediaSourceTiers != null

    companion object {

        val Initial = MediaSelectorContext(null, null, null, null, null, null, null)

        val EmptyForPreview
            get() = MediaSelectorContext(
                false,
                emptyList(),
                MediaSelectorSubtitlePreferences.AllNormal,
                SubjectSeriesInfo.Fallback,
                SubjectInfo.Empty,
                EpisodeInfo.Empty,
                mediaSourceTiers = MediaSelectorSourceTiers.Empty,
            )

        internal val logger = logger<MediaSelectorContext>()
    }
}

class MediaSelectorContextFlowProducer(
    subjectCompleted: Flow<Boolean>,
    mediaSourcePrecedence: Flow<List<String>>,
    subjectSeriesInfo: Flow<SubjectSeriesInfo>,
    subjectInfoFlow: Flow<SubjectInfo>,
    episodeInfoFlow: Flow<EpisodeInfo>,
    mediaSourceTiersFlow: Flow<MediaSelectorSourceTiers>,
    subtitleKindFilters: Flow<MediaSelectorSubtitlePreferences> = flowOf(MediaSelectorSubtitlePreferences.CurrentPlatform),
) {
    val flow = com.wynime.utils.coroutines.flows.combine(

        subjectCompleted.onStart<Boolean?> { emit(null) },
        mediaSourcePrecedence.onStart<List<String>?> { emit(null) },
        subtitleKindFilters.onStart<MediaSelectorSubtitlePreferences?> { emit(null) },
        subjectSeriesInfo.retryWithBackoffDelay { e, _ ->
            val wrapped = RepositoryException.wrapOrThrowCancellation(e)
            if (wrapped is RepositoryUnknownException) {
                MediaSelectorContext.Companion.logger.warn { "Failed to load related subject names due to $wrapped" }
            } else {
                MediaSelectorContext.Companion.logger.error(wrapped) { "Failed to load related subject names" }
            }
            emit(SubjectSeriesInfo.Fallback)
            true
        }.onStart<SubjectSeriesInfo?> { emit(null) },
        subjectInfoFlow.onStart<SubjectInfo?> { emit(null) },
        episodeInfoFlow.onStart<EpisodeInfo?> { emit(null) },
        mediaSourceTiersFlow.onStart<MediaSelectorSourceTiers?> { emit(null) },
    ) { completed, instances, filters, seriesInfo, subjectInfo, episodeInfo, mediaSourceTiers ->
        MediaSelectorContext(
            subjectFinished = completed,
            mediaSourcePrecedence = instances,
            subtitlePreferences = filters,
            subjectSeriesInfo = seriesInfo,
            subjectInfo = subjectInfo,
            episodeInfo = episodeInfo,
            mediaSourceTiers = mediaSourceTiers,
        )
    }.onStart {
        emit(Initial)
    }
}

data class MediaSelectorSourceTiers(

    val tiers: Map<String, MediaSourceTier>,

    val channelTiers: Map<String, Map<String, MediaSourceTier>> = emptyMap(),
    val fallback: (mediaSourceId: String) -> MediaSourceTier = { MediaSourceTier.Fallback },
) {
    operator fun get(mediaSourceId: String): MediaSourceTier {
        return tiers[mediaSourceId] ?: fallback(mediaSourceId)
    }

    fun get(mediaSourceId: String, channel: String?): MediaSourceTier {
        if (!channel.isNullOrEmpty()) {
            channelTiers[mediaSourceId]?.get(channel)?.let { return it }
        }
        return get(mediaSourceId)
    }

    fun getBestTier(mediaSourceId: String): MediaSourceTier {
        val sourceTier = get(mediaSourceId)
        val channelMin = channelTiers[mediaSourceId]?.values?.minOrNull() ?: return sourceTier
        return minOf(sourceTier, channelMin)
    }

    companion object {
        val Empty = MediaSelectorSourceTiers(emptyMap())
    }
}
