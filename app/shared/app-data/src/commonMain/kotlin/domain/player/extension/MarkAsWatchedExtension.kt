package com.wynime.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.episode.toEpisodeInfo
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.episode.GetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.koin.core.Koin
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class MarkAsWatchedExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension("AutoMarkWatched") {
    private val getVideoScaffoldConfig: GetVideoScaffoldConfigUseCase by koin.inject()
    private val getEpisodeCollectionType: GetEpisodeCollectionTypeUseCase by koin.inject()
    private val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase by koin.inject()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("AutoMarkWatched") {
            var marked = false
            getVideoScaffoldConfig().map { it.autoMarkDone }.distinctUntilChanged().collectLatest { enabled ->
                if (!enabled || marked) return@collectLatest
                val player = context.player
                combine(
                    player.currentPositionMillis,
                    player.mediaProperties.map { it?.durationMillis }.distinctUntilChanged(),
                    player.state.map { it.isPlaying }.distinctUntilChanged(),
                ) { position, duration, playing ->
                    playing && duration != null && positionMeetsThreshold(position, duration)
                }.first { it }

                try {
                    val subjectId = context.subjectId
                    val episodeId = episodeSession.episodeId
                    if (getEpisodeCollectionType(subjectId, episodeId, allowNetwork = false)?.isDoneOrDropped() == true) {
                        marked = true
                        return@collectLatest
                    }
                    val cachedEpisode = episodeSession.loadedMedia.value?.cacheMetadata?.toEpisodeInfo()
                    if (cachedEpisode != null) {
                        setEpisodeCollectionType.setKnownEpisodeCollectionType(subjectId, cachedEpisode, UnifiedCollectionType.DONE)
                    } else {
                        setEpisodeCollectionType(subjectId, episodeId, UnifiedCollectionType.DONE)
                    }
                    marked = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to mark episode ${episodeSession.episodeId} as watched" }
                }
            }
        }
    }

    private fun positionMeetsThreshold(positionMillis: Long, durationMillis: Long): Boolean {
        if (durationMillis < 10.seconds.inWholeMilliseconds) return false
        val percentThreshold = durationMillis - durationMillis / 10
        val endingThreshold = durationMillis - 100.seconds.inWholeMilliseconds
        val threshold = if (endingThreshold > 0) minOf(percentThreshold, endingThreshold) else percentThreshold
        return positionMillis >= threshold
    }

    companion object : EpisodePlayerExtensionFactory<MarkAsWatchedExtension> {
        private val logger = logger<MarkAsWatchedExtension>()

        override fun create(context: PlayerExtensionContext, koin: Koin): MarkAsWatchedExtension =
            MarkAsWatchedExtension(context, koin)
    }
}
