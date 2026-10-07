package com.wynime.app.domain.player.extension

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.domain.media.selector.eventHandling
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.mediasource.SetPreferredWebMediaSourceUseCase
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.core.Koin

class ObserveWebMediaSourcePreferenceExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("ObserveWebMediaSourcePreference") {
    private val getPreferredWebMediaSource: GetPreferredWebMediaSourceUseCase by koin.inject()
    private val setPreferredWebMediaSource: SetPreferredWebMediaSourceUseCase by koin.inject()

    private val logger = logger<ObserveWebMediaSourcePreferenceExtension>()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("ObserveWebMediaSourcePreference") {
            context.sessionFlow.flatMapLatest { it.fetchSelectFlow }.collectLatest { bundle ->
                if (bundle == null) return@collectLatest
                coroutineScope {

                    launch {
                        bundle.mediaSelector.eventHandling.preferWebMediaSource { event ->
                            if (event.subjectId != context.subjectId) return@preferWebMediaSource
                            val currentPreference = getPreferredWebMediaSource(event.subjectId).first()
                            if (currentPreference != event.mediaSourceId) {
                                logger.info { "Set web source preference for subject ${context.subjectId} to ${event.mediaSourceId}" }
                                setPreferredWebMediaSource(event.subjectId, event.mediaSourceId)
                            }
                        }
                    }

                    combine(

                        getPreferredWebMediaSource(context.subjectId).filterNotNull(),
                        combine(
                            bundle.mediaFetchSession.mediaSourceResults
                                .filter { it.kind == MediaSourceKind.WEB }
                                .map { r -> r.state.map { r } },
                            Array<MediaSourceFetchResult>::toList,
                        ),
                    ) { preferredWebMediaSourceId, results ->
                        results.forEach {
                            if (it.mediaSourceId != preferredWebMediaSourceId) return@forEach
                            if (it.state.value is MediaSourceFetchState.Failed) {
                                logger.info {
                                    "Remove web source preference for subject ${context.subjectId} from ${it.mediaSourceId}. " +
                                            "because source state in this session is ${it.state.value.str()}."
                                }
                                setPreferredWebMediaSource(context.subjectId, null)
                            }
                        }
                    }.launchIn(this)
                }
            }
        }
    }

    private fun MediaSourceFetchState.str(): String {
        return when (this) {
            is MediaSourceFetchState.Failed -> "failed"
            is MediaSourceFetchState.Abandoned -> "abandoned"
            else -> this::class.simpleName!!.lowercase()
        }
    }

    companion object : EpisodePlayerExtensionFactory<ObserveWebMediaSourcePreferenceExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): ObserveWebMediaSourcePreferenceExtension {
            return ObserveWebMediaSourcePreferenceExtension(context, koin)
        }
    }
}