package com.wynime.app.domain.episode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.wynime.app.domain.media.hls.HlsPlaybackOptions
import com.wynime.app.domain.media.hls.HlsPlaybackPreparer
import com.wynime.app.domain.media.hls.HlsPlaybackProxySession
import com.wynime.app.domain.media.player.prefetch.MediaPrefetchController
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.domain.media.resolver.MediaResolutionException
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.MediaSourceOpenException
import com.wynime.app.domain.media.resolver.OpenFailures
import com.wynime.app.domain.media.resolver.ResolutionFailures
import com.wynime.app.domain.media.resolver.UnsupportedMediaException
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.player.VideoLoadingState
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.app.domain.sourceplugin.SourcePluginFailure
import com.wynime.app.domain.sourceplugin.safeSourceUrl
import com.wynime.app.domain.sourceplugin.sourceFailureDiagnostics
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.source.plugin.api.SourceResultStatus
import com.wynime.source.plugin.api.SourceTracePhase
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackException
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.CoroutineContext

class MediaFetchSelectBundle(
    val mediaFetchSession: MediaFetchSession,
    val mediaSelector: MediaSelector,
)

class PlayerSession(
    val player: MediampPlayer,
    koin: Koin,
    backgroundScope: CoroutineScope,
    private val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
) {
    val mediaResolver: MediaResolver by koin.inject()
    private val hlsPlaybackPreparer: HlsPlaybackPreparer by koin.inject()
    private val getVideoScaffoldConfigUseCase: GetVideoScaffoldConfigUseCase by koin.inject()

    private val hlsPlaybackProxySessionFlow = MutableStateFlow<HlsPlaybackProxySession?>(null)
    private var hlsPlaybackProxySession: HlsPlaybackProxySession?
        get() = hlsPlaybackProxySessionFlow.value
        set(value) {
            hlsPlaybackProxySessionFlow.value = value
        }

    val prefetchController: MediaPrefetchController = MediaPrefetchController(
        player,
        hlsPlaybackProxySessionFlow,
        backgroundScope,
    )

    private val _videoLoadingStateFlow: MutableStateFlow<VideoLoadingState> =
        MutableStateFlow(VideoLoadingState.Initial)

    val videoLoadingState: StateFlow<VideoLoadingState> get() = _videoLoadingStateFlow.asStateFlow()

    suspend fun loadMedia(media: Media?, episodeInfo: EpisodeMetadata) = coroutineScope {
        val backgroundScope = this
        _videoLoadingStateFlow.value = VideoLoadingState.Initial
        stopPlayback()
        if (media == null) {
            return@coroutineScope
        }

        var preparedHlsPlaybackProxySession: HlsPlaybackProxySession? = null
        try {
            _videoLoadingStateFlow.value = VideoLoadingState.ResolvingSource
            val source = mediaResolver.resolve(
                media,
                episodeInfo,
            )
            _videoLoadingStateFlow.compareAndSet(
                VideoLoadingState.ResolvingSource,
                VideoLoadingState.DecodingData,
            )

            val data = source.open(scopeForCleanup = backgroundScope)
            val preparedData = prepareHlsPlaybackIfEnabled(data).also {
                preparedHlsPlaybackProxySession = it.session
            }.data

            logger.info { "Set media data to player: $preparedData" }

            player.setMediaData(preparedData, playWhenReady = true)
            hlsPlaybackProxySession = preparedHlsPlaybackProxySession
            preparedHlsPlaybackProxySession = null
            traceSourcePlaybackResult(media, SourceResultStatus.SUCCESS)

            _videoLoadingStateFlow.value = VideoLoadingState.Succeed
        } catch (e: UnsupportedMediaException) {
            logger.warn { IllegalStateException("Failed to resolve video source, unsupported media", e) }
            _videoLoadingStateFlow.value = VideoLoadingState.UnsupportedMedia
            stopPlayback()
        } catch (e: MediaSourceOpenException) {
            val sourceError = sourcePlaybackError(
                media = media,
                status = SourceResultStatus.MEDIA_UNREACHABLE,
                reason = e.reason.name,
                retryable = true,
            )
            if (sourceError != null) {
                _videoLoadingStateFlow.value = sourceError
            } else {
                logger.warn {
                    IllegalStateException(
                        "Failed to resolve video source due to VideoSourceOpenException",
                        e,
                    )
                }
                _videoLoadingStateFlow.value = when (e.reason) {
                    OpenFailures.NO_MATCHING_FILE -> VideoLoadingState.NoMatchingFile
                    OpenFailures.UNSUPPORTED_VIDEO_SOURCE -> VideoLoadingState.UnsupportedMedia
                    OpenFailures.ENGINE_DISABLED -> VideoLoadingState.UnsupportedMedia
                }
            }
            stopPlayback()
        } catch (e: SourcePluginFailure) {
            traceSourcePlaybackResult(media, e.status, e.diagnostics.failureReason)
            logger.warn {
                "Source plugin playback failed: status=${e.status.name} " +
                    "provider=${e.diagnostics.provider} traceId=${e.diagnostics.traceId}"
            }
            _videoLoadingStateFlow.value = VideoLoadingState.SourceError(
                status = e.status,
                diagnostics = e.diagnostics,
                requiresVerification = e.requiresVerification,
                retryable = e.retryable,
            )
            stopPlayback()
        } catch (e: MediaResolutionException) {
            logger.warn {
                IllegalStateException(
                    "Failed to resolve video source due to VideoSourceResolutionException",
                    e,
                )
            }
            _videoLoadingStateFlow.value = when (e.reason) {
                ResolutionFailures.FETCH_TIMEOUT -> VideoLoadingState.ResolutionTimedOut
                ResolutionFailures.ENGINE_ERROR -> VideoLoadingState.UnknownError(e)
                ResolutionFailures.NETWORK_ERROR -> VideoLoadingState.NetworkError
                ResolutionFailures.NO_MATCHING_RESOURCE -> VideoLoadingState.NoMatchingFile
            }
            stopPlayback()
        } catch (e: CancellationException) {
            _videoLoadingStateFlow.value = VideoLoadingState.Cancelled
            throw e
        } catch (e: LinkageError) {
            val sourceError = sourcePlaybackError(
                media = media,
                status = SourceResultStatus.PLUGIN_ERROR,
                reason = e::class.simpleName,
                retryable = false,
            )
            if (sourceError != null) {
                _videoLoadingStateFlow.value = sourceError
            } else {
                logger.error { IllegalStateException("Plugin linkage failure while opening media", e) }
                _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            }
            stopPlayback()
        } catch (e: Error) {
            throw e
        } catch (e: ClassCastException) {
            val sourceError = sourcePlaybackError(
                media = media,
                status = SourceResultStatus.PLUGIN_ERROR,
                reason = e::class.simpleName,
                retryable = false,
            )
            if (sourceError != null) {
                _videoLoadingStateFlow.value = sourceError
            } else {
                logger.error { IllegalStateException("Plugin contract type failure while opening media", e) }
                _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            }
            stopPlayback()
        } catch (e: PlaybackException) {
            val sourceError = sourcePlaybackError(
                media = media,
                status = SourceResultStatus.PLAYBACK_ERROR,
                reason = e::class.simpleName,
                retryable = false,
            )
            if (sourceError != null) {
                _videoLoadingStateFlow.value = sourceError
            } else {
                traceSourcePlaybackResult(media, SourceResultStatus.PLAYBACK_ERROR, e::class.simpleName)
                logger.warn { IllegalStateException("Player rejected the media data", e) }
                _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            }
            stopPlayback()
        } catch (e: Throwable) {
            val sourceError = sourcePlaybackError(
                media = media,
                status = SourceResultStatus.PLAYBACK_ERROR,
                reason = e::class.simpleName,
                retryable = true,
            )
            if (sourceError != null) {
                _videoLoadingStateFlow.value = sourceError
            } else {
                traceSourcePlaybackResult(media, SourceResultStatus.PLAYBACK_ERROR, e::class.simpleName)
                logger.error { IllegalStateException("Failed to resolve video source with unknown error", e) }
                _videoLoadingStateFlow.value = VideoLoadingState.UnknownError(e)
            }
            stopPlayback()
        } finally {
            preparedHlsPlaybackProxySession?.close()
        }
    }

    suspend fun stopPlayback() {
        stopPlayer()
        closeHlsPlaybackProxySession()
    }

    fun close() {
        closeHlsPlaybackProxySession()
        player.close()
    }

    private suspend fun stopPlayer() {
        withContext(mainDispatcher) {
            player.stopPlayback()
        }
    }

    private suspend fun prepareHlsPlaybackIfEnabled(data: MediaData): PreparedMediaData {
        if (data !is UriMediaData) {
            return PreparedMediaData(data)
        }
        val config = getVideoScaffoldConfigUseCase.invoke().first()
        val options = HlsPlaybackOptions(
            filterSegments = config.enableExperimentalHlsSegmentFiltering,

            proxySegments = config.autoSkipOpEd,
        )
        if (!options.isEnabled) {
            return PreparedMediaData(data)
        }
        val result = hlsPlaybackPreparer.prepare(data, options)
        return PreparedMediaData(result.data, result.session)
    }

    private fun closeHlsPlaybackProxySession() {
        hlsPlaybackProxySession?.close()
        hlsPlaybackProxySession = null
    }

    private fun traceSourcePlaybackResult(media: Media?, status: SourceResultStatus, detail: String? = null) {
        val reference = media?.download as? ResourceLocation.SourcePluginMedia ?: return
        val traceId = reference.traceId.ifBlank { "-" }
        logger.info {
            "source_trace phase=PLAYBACK_RESULT traceId=$traceId provider=${reference.pluginId} " +
                "status=${status.name} url=${safeSourceUrl(reference.uri)} detail=${detail.orEmpty()}"
        }
    }

    private fun sourcePlaybackError(
        media: Media?,
        status: SourceResultStatus,
        reason: String?,
        retryable: Boolean,
    ): VideoLoadingState.SourceError? {
        val reference = media?.download as? ResourceLocation.SourcePluginMedia ?: return null
        val diagnostics = sourceFailureDiagnostics(
            traceId = reference.traceId.ifBlank { "-" },
            provider = reference.pluginId,
            entryPoint = SourceTracePhase.PLAYBACK_RESULT.name,
            status = status,
            url = reference.uri,
            failureReason = reason,
        )
        traceSourcePlaybackResult(media, status, reason)
        return VideoLoadingState.SourceError(
            status = status,
            diagnostics = diagnostics,
            requiresVerification = false,
            retryable = retryable,
        )
    }

    companion object {
        private val logger = logger<PlayerSession>()
    }

    private data class PreparedMediaData(
        val data: MediaData,
        val session: HlsPlaybackProxySession? = null,
    )
}

