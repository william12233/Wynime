package com.wynime.app.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import com.wynime.app.domain.media.selector.trace.MediaSelectionTraceRecorder
import com.wynime.app.navigation.WynimeNavigator
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.koin.core.Koin
import org.koin.dsl.module
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class DesktopMediaTraceCapture private constructor(
    private val scope: CoroutineScope,
    private val duration: Duration,
    private val episodes: List<Pair<Int, Int>>,
    private val subjects: SubjectCollectionRepository,
) {
    fun start(navigator: WynimeNavigator) {
        if (episodes.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            navigator.awaitBackStack()
            delay(2.seconds)
            for ((subjectId, episodeId) in episodes) {

                val metadata = withTimeoutOrNull(30.seconds) {
                    subjects.subjectCollectionFlow(subjectId).first { subject ->
                        subject.episodes.any { it.episodeId == episodeId }
                    }
                }
                if (metadata == null) {
                    logger.warn { "Skipping capture: episode metadata unavailable for $subjectId:$episodeId" }
                    continue
                }
                logger.info { "Opening real playback for capture: subject=$subjectId, episode=$episodeId" }
                navigator.navigateEpisodeDetails(subjectId, episodeId)
                delay(duration + 15.seconds)
            }
            logger.info { "Playback capture batch finished" }
        }
    }

    companion object {
        private val logger = logger<DesktopMediaTraceCapture>()

        fun install(koin: Koin, scope: CoroutineScope): DesktopMediaTraceCapture? {
            val directory = System.getenv("ANIMEKO_MEDIA_TRACE_DIR")?.takeIf { it.isNotBlank() } ?: return null
            val duration = (System.getenv("ANIMEKO_MEDIA_TRACE_SECONDS")?.toLongOrNull() ?: 45).seconds
            require(duration.isPositive() && duration.isFinite())
            val episodes = System.getenv("ANIMEKO_MEDIA_TRACE_EPISODES").orEmpty()
                .split(',').filter { it.isNotBlank() }.map { value ->
                    val ids = value.split(':')
                    require(ids.size == 2) { "Expected subjectId:episodeId" }
                    ids[0].toInt() to ids[1].toInt()
                }
            val recorder = MediaSelectionTraceRecorder(File(directory), koin, duration)
            val delegate = koin.get<CreateMediaFetchSelectBundleFlowUseCase>()
            koin.loadModules(listOf(module {
                single<CreateMediaFetchSelectBundleFlowUseCase> { recorder.decorate(delegate) }
            }))
            return DesktopMediaTraceCapture(
                scope, duration, episodes, koin.get<SubjectCollectionRepository>(),
            )
        }
    }
}
