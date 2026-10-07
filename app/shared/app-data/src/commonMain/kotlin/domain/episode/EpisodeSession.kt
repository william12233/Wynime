package com.wynime.app.domain.episode

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaFetcher
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.utils.coroutines.childScope
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext

class EpisodeSession(
    subjectId: Int,
    val episodeId: Int,
    koin: Koin,
    parentCoroutineContext: CoroutineContext,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
    fetchSessions: SubjectMediaFetchSessions? = null,
) {
    private val createMediaFetchSelectBundleFlowUseCase: CreateMediaFetchSelectBundleFlowUseCase by koin.inject()

    internal val sessionScope =
        parentCoroutineContext.childScope(CoroutineName("SubjectEpisodeFetchSelectSession"))

    internal val sessionScopeTasksStarted = MutableStateFlow(false)

    private val infoLoader = SubjectEpisodeInfoBundleLoader(
        subjectId,
        flowOf(episodeId),
        koin,
    )

    val infoBundleFlow: SharedFlow<SubjectEpisodeInfoBundle?> = infoLoader.infoBundleFlow
        .shareIn(sessionScope, started = sharingStarted, replay = 1)

    val infoLoadErrorStateFlow: StateFlow<LoadError?> get() = infoLoader.infoLoadErrorState

    val fetchSelectFlow = (
            if (fetchSessions == null) createMediaFetchSelectBundleFlowUseCase(infoBundleFlow)
            else createMediaFetchSelectBundleFlowUseCase(infoBundleFlow, fetchSessions)
            ).shareIn(sessionScope, sharingStarted, replay = 1)

    val mediaSourceLoadingFlow = fetchSelectFlow
        .transformLatest { bundle ->
            if (bundle == null) {
                emit(false)
                return@transformLatest
            }
            emitAll(
                bundle.mediaFetchSession.hasCompleted.map {
                    !it.allCompleted()
                },
            )
        }

    fun restartLoad() {
        infoLoader.restart()
    }
}
