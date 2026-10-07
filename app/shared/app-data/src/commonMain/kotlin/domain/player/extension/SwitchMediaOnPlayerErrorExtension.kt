package com.wynime.app.domain.player.extension

import androidx.annotation.VisibleForTesting
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.episode.MediaFetchSelectBundle
import com.wynime.app.domain.media.DroppedFileMedia
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.selector.MediaAutoSelector
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlayerState

class SwitchMediaOnPlayerErrorExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("SwitchMediaOnPlayerErrorExtension") {
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()
    private val getMediaSelectorSettingsFlowUseCase: GetMediaSelectorSettingsFlowUseCase by koin.inject()
    private val getSourceTiersUseCase: GetMediaSelectorSourceTiersUseCase by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("PlayerErrorListener") {
            context.sessionFlow.collectLatest { session ->
                invoke(
                    session.fetchSelectFlow,
                    context.videoLoadingStateFlow,
                    context.player.state,
                )
            }
        }
    }

    private suspend fun invoke(
        mediaFetchSessionFlow: Flow<MediaFetchSelectBundle?>,
        videoLoadingStateFlow: Flow<VideoLoadingState>,
        playerStateFlow: Flow<PlayerState>
    ) {
        val handler = PlayerLoadErrorHandler(
            getPreferKind = { getMediaSelectorSettingsFlowUseCase().first().preferKind },
            getSourceTiers = { getSourceTiersUseCase().first() },
        )

        getVideoScaffoldConfigUseCase().map { it.autoSwitchMediaOnPlayerError }
            .collectLatest { autoSwitchMediaOnPlayerError ->
                if (!autoSwitchMediaOnPlayerError) {

                    return@collectLatest
                }

                coroutineScope {
                    launch {
                        handler.observeMediaSelectorBlacklist(
                            mediaFetchSessionFlow.mapNotNull { it?.mediaSelector },
                        )
                    }

                    launch {
                        handler.observeLoadErrorAndHandle(
                            mediaFetchSessionFlow,
                            videoLoadingStateFlow,
                            playerStateFlow,
                        )
                    }
                }
            }
    }

    private suspend fun PlayerLoadErrorHandler.observeLoadErrorAndHandle(
        mediaFetchSessionFlow: Flow<MediaFetchSelectBundle?>,
        videoLoadingStateFlow: Flow<VideoLoadingState>,
        playerStateFlow: Flow<PlayerState>
    ) {
        mediaFetchSessionFlow.collectLatest { bundle ->
            if (bundle == null) return@collectLatest

            combine(
                videoLoadingStateFlow,
                playerStateFlow,
            ) { videoLoadingState, playerState ->
                videoLoadingState is VideoLoadingState.Failed || playerState.mediaStatus is MediaStatus.Error
            }.distinctUntilChanged()
                .collectLatest { isError ->
                    if (isError) {
                        handleError(bundle.mediaFetchSession, bundle.mediaSelector)
                    }
                }
        }
    }

    companion object : EpisodePlayerExtensionFactory<SwitchMediaOnPlayerErrorExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): SwitchMediaOnPlayerErrorExtension {
            return SwitchMediaOnPlayerErrorExtension(context, koin)
        }
    }
}

internal class PlayerLoadErrorHandler(
    private val getPreferKind: suspend () -> MediaSourceKind?,
    private val getSourceTiers: suspend () -> MediaSelectorSourceTiers,
) {
    private var blacklistedMediaIds = persistentHashSetOf<String>()

    suspend fun observeMediaSelectorBlacklist(
        mediaSelectorFlow: Flow<MediaSelector>
    ) {
        mediaSelectorFlow.collectLatest { selector ->
            selector.events.onSelect.collect { event ->
                event.previousMedia?.let {
                    blacklistedMediaIds = blacklistedMediaIds.add(it.mediaId)
                }
            }
        }
    }

    suspend fun handleError(
        session: MediaFetchSession,
        mediaSelector: MediaSelector,
    ) {
        val failedMedia = mediaSelector.selected.value
        if (failedMedia != null && DroppedFileMedia.isDroppedFile(failedMedia)) {

            logger.info { "Player errored on a dropped file, skip automatic switch" }
            return
        }

        logger.info { "Player errored, automatically switching to next media" }

        failedMedia?.let {
            blacklistedMediaIds = blacklistedMediaIds.add(it.mediaId)
        }

        delay(1.seconds)
        if (mediaSelector.selected.value != failedMedia) return

        val (preferKind, sourceTiers) = combine(
            getPreferKind.asFlow(),
            getSourceTiers.asFlow(),
        ) { kind, tiers -> kind to tiers }.first()

        if (preferKind != MediaSourceKind.WEB) {
            logger.info { "Player errored, but preferKind is not WEB ($preferKind), skip automatic switch" }
            return
        }

        val result = MediaAutoSelector(mediaSelector).select(
            session,
            MediaAutoSelector.Config(
                selectCache = false,
                blacklist = blacklistedMediaIds,
                web = MediaAutoSelector.Web(
                    sourceTiers = sourceTiers,

                    exactMatchAfter = 1.seconds,
                    fuzzyMatchAfter = 1.seconds,
                    waitForPendingSources = false,
                ),
            ),
            expectedSelection = failedMedia,
        )
        logger.info { "Player errored, automatically switched to next media: $result" }
    }

    companion object {
        private val logger = logger<PlayerLoadErrorHandler>()
    }

    @VisibleForTesting
    val blacklist: Set<String> get() = blacklistedMediaIds
}
