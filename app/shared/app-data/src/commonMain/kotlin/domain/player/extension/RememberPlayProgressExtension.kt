package com.wynime.app.domain.player.extension

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.data.repository.player.PlaybackHistorySyncer
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.source.MediaData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RememberPlayProgressExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
    private val periodicReportInterval: Duration = 1.minutes,
    private val initialReportDelay: Duration = 5.seconds,
) : PlayerExtension(name = "SaveProgressExtension") {
    private val playProgressRepository: EpisodePlayHistoryRepository by koin.inject()
    private val historySyncer = koin.getOrNull<PlaybackHistorySyncer>()
    private val progressMutex = Mutex()
    private val restoredPlayback = MutableStateFlow<RestoredPlayback?>(null)

    private data class RestoredPlayback(val media: EpisodeSession.LoadedMedia, val savedPosition: Long?)

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        historySyncer?.let { syncer ->
            backgroundTaskScope.launch("ProgressCheckpoint") {
                val unregister = syncer.registerProgressCheckpoint {
                    savePlayProgressOrRemove(episodeSession)
                }
                try {
                    awaitCancellation()
                } finally {
                    unregister()
                }
            }
        }
        backgroundTaskScope.launch("MediaSelectorListener") {
            episodeSession.fetchSelectFlow.collectLatest { fetchSelect ->
                fetchSelect?.mediaSelector?.events?.onBeforeSelect?.collect {
                    savePlayProgressOrRemove(episodeSession)
                }
            }
        }

        backgroundTaskScope.launch("PlaybackStateListener") {
            val player = context.player
            var restoredDirectMedia: MediaData? = null
            combine(player.state, episodeSession.loadedMedia) { state, media -> state to media }
                .collectLatest { (state, media) ->
                    if (media == null) {
                        val directMedia = player.mediaData.value
                        if (state.isPlaying && !episodeSession.managesPlayback.value &&
                            directMedia != null && directMedia !== restoredDirectMedia
                        ) {
                            restoreSavedPosition(
                                episodeSession.episodeId,
                                isCurrent = { !episodeSession.managesPlayback.value && player.mediaData.value === directMedia },
                                onRestored = { restoredDirectMedia = directMedia },
                            )
                        }
                        return@collectLatest
                    }
                    when {
                        state.isPlaying -> {
                            restorePosition(episodeSession, media)
                            delay(initialReportDelay)
                            savePlayProgressOrRemove(episodeSession, allowZeroPosition = true)

                            if (periodicReportInterval != Duration.INFINITE) {
                                while (true) {
                                    delay(periodicReportInterval)
                                    savePlayProgressOrRemove(episodeSession, allowZeroPosition = true)
                                }
                            }
                        }

                        state.mediaStatus == MediaStatus.Ready && !state.playWhenReady -> {
                            restorePosition(episodeSession, media)
                            savePlayProgressOrRemove(episodeSession)
                        }

                        state.mediaStatus == MediaStatus.Ended -> savePlayProgressOrRemove(episodeSession)
                    }
                }
        }
    }

    private suspend fun restorePosition(session: EpisodeSession, media: EpisodeSession.LoadedMedia) {
        if (restoredPlayback.value?.media === media) return
        if (media.cacheMetadata == null && session.loadedMedia.value === media) {
            historySyncer?.pullBeforePlayback()
        }
        restoreSavedPosition(
            session.episodeId,
            isCurrent = { session.loadedMedia.value === media },
            onRestored = { position -> restoredPlayback.value = RestoredPlayback(media, position) },
        )
    }

    private suspend fun restoreSavedPosition(
        episodeId: Int,
        isCurrent: () -> Boolean,
        onRestored: (Long?) -> Unit,
    ) {
        val position = playProgressRepository.getPositionMillisByEpisodeId(episodeId)
        if (position != null && position > 0) {
            val properties = context.player.mediaProperties.first { (it?.durationMillis ?: 0L) > 0L }
            withContext(Dispatchers.Main.immediate + NonCancellable) {
                if (isCurrent()) {
                    if (position < (properties?.durationMillis ?: 0L)) {
                        logger.info { "Seeking to saved position: $position" }
                        context.player.seekTo(position)
                    }
                    onRestored(position)
                }
            }
        } else if (isCurrent()) {
            onRestored(position)
        }
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        savePlayProgressOrRemove(context.sessionFlow.first())
    }

    override suspend fun onClose() {
        savePlayProgressOrRemove(context.sessionFlow.first())
    }

    private suspend fun savePlayProgressOrRemove(
        session: EpisodeSession,
        allowZeroPosition: Boolean = false,
    ) {
        progressMutex.withLock {
            val media = session.loadedMedia.value ?: return
            val restored = restoredPlayback.value?.takeIf { it.media === media } ?: return
            val player = context.player
            val videoDurationMillis = player.mediaProperties.value?.durationMillis ?: return
            if (videoDurationMillis <= 0L) return

            val mediaStatus = player.state.value.mediaStatus
            if (mediaStatus != MediaStatus.Ready && mediaStatus != MediaStatus.Ended) return

            val position = player.currentPositionMillis.value
            if (position < 0L || (position == 0L && (!allowZeroPosition || (restored.savedPosition ?: 0L) > 0L))) {
                return
            }

            val info = session.infoBundleFlow.replayCache.lastOrNull()
            val cache = media.cacheMetadata
            withContext(NonCancellable) {
                if (videoDurationMillis - position < 5000) {
                    playProgressRepository.remove(session.episodeId)
                } else {
                    playProgressRepository.saveOrUpdate(
                        episodeId = session.episodeId,
                        positionMillis = position,
                        subjectId = context.subjectId,
                        episodeSort = info?.episodeInfo?.sort?.number ?: cache?.episodeSort?.number,
                        subjectName = info?.subjectInfo?.displayName ?: cache?.subjectNameCN?.takeIf { it.isNotBlank() }
                            ?: cache?.subjectNames?.firstOrNull { it.isNotBlank() },
                        subjectImageUrl = info?.subjectInfo?.imageLarge,
                        episodeName = info?.episodeInfo?.displayName ?: cache?.episodeName,
                        durationMillis = videoDurationMillis,
                    )
                }
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<RememberPlayProgressExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): RememberPlayProgressExtension =
            RememberPlayProgressExtension(context, koin)

        private val logger = logger<RememberPlayProgressExtension>()
    }
}
