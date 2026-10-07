package com.wynime.app.domain.player.extension

import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.episode.GetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.utils.coroutines.cancellableCoroutineScope
import com.wynime.utils.coroutines.sampleWithInitial
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

class MarkAsWatchedExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val enableSamplingAndDebounce: Boolean,
) : PlayerExtension("AutoMarkWatched") {
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()
    private val getEpisodeCollectionTypeUseCase: GetEpisodeCollectionTypeUseCase by koin.inject()
    private val setEpisodeCollectionTypeUseCase: SetEpisodeCollectionTypeUseCase by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope,
    ) {
        backgroundTaskScope.launch("AutoMarkWatched") {
            context.sessionFlow.collectLatest { session ->
                invoke(
                    context.player,
                    context.subjectId,
                    session.episodeId,
                )
            }
        }
    }

    private suspend fun invoke(
        player: MediampPlayer,
        subjectId: Int,
        episodeId: Int,
    ) {
        getVideoScaffoldConfigUseCase()
            .map { it.autoMarkDone }
            .distinctUntilChanged()
            .collectLatest { enabled ->
                if (!enabled) return@collectLatest

                impl(episodeId, player, subjectId)
            }

    }

    private suspend fun impl(
        episodeId: Int,
        player: MediampPlayer,
        subjectId: Int,
    ) {
        val collectionType =
            getEpisodeCollectionTypeUseCase(
                subjectId,
                episodeId,
                allowNetwork = false,
            )
        if (collectionType?.isDoneOrDropped() == true) {

            return
        }

        cancellableCoroutineScope {
            combine(
                player.currentPositionMillis
                    .let { if (enableSamplingAndDebounce) it.sampleWithInitial(5000) else it },
                player.mediaProperties.map { it?.durationMillis }
                    .let { if (enableSamplingAndDebounce) it.debounce(5000) else it },
                player.state,
            ) { pos, videoLength, state ->
                if (videoLength == null || !state.isPlaying) return@combine
                if (videoLength < 10.seconds.inWholeMilliseconds) return@combine
                if (pos >=
                    min(
                        (videoLength.toFloat() * 0.9).toLong(),
                        videoLength - 100.seconds.inWholeMilliseconds,
                    )
                ) {
                    logger.info { "观看到 90%, 标记看过" }
                    try {
                        setEpisodeCollectionTypeUseCase(subjectId, episodeId, UnifiedCollectionType.DONE)
                    } catch (e: ClientRequestException) {
                        logger.warn("Failed to setEpisodeCollectionTypeUseCase, see cause", e)
                    }
                    cancelScope()
                }
            }.collect()
        }
    }

    companion object : EpisodePlayerExtensionFactory<MarkAsWatchedExtension> {
        private val logger = logger<MarkAsWatchedExtension>()

        override fun create(context: PlayerExtensionContext, koin: Koin): MarkAsWatchedExtension {
            return MarkAsWatchedExtension(context, koin, enableSamplingAndDebounce = true)
        }
    }
}