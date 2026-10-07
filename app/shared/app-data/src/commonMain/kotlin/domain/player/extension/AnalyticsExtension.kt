package com.wynime.app.domain.player.extension

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.EpisodePlaying
import org.koin.core.Koin

class AnalyticsExtension(
    private val context: PlayerExtensionContext
) : PlayerExtension("AnalyticsStartPlay") {
    private val hasPlayedOnce = MutableStateFlow(false)

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("AnalyticsStartPlay") {
            hasPlayedOnce.collectLatest { played ->
                if (played) return@collectLatest

                context.player.state.collectLatest { state ->
                    if (state.isPlaying) {
                        Analytics.recordEvent(
                            EpisodePlaying,
                            mapOf(
                                "subject_id" to context.subjectId,
                                "episode_id" to episodeSession.episodeId,
                            ),
                        )
                        hasPlayedOnce.value = true
                    }
                }
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<AnalyticsExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): AnalyticsExtension {
            return AnalyticsExtension(context)
        }
    }
}