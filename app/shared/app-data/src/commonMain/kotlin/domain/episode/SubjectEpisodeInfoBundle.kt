package com.wynime.app.domain.episode

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.domain.foundation.FlowLoadErrorObserver
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.foundation.catchLoadError
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.Koin

data class SubjectEpisodeInfoBundle(
    val subjectId: Int,
    val episodeId: Int,

    val subjectCollectionInfo: SubjectCollectionInfo,
    val episodeCollectionInfo: EpisodeCollectionInfo,

    val seriesInfo: SubjectSeriesInfo,
    val subjectCompleted: Boolean,
) {
    fun anyLoading() = false

    val subjectInfo: SubjectInfo get() = subjectCollectionInfo.subjectInfo

    val episodeInfo: EpisodeInfo get() = episodeCollectionInfo.episodeInfo
}

@TestOnly
fun createTestSubjectEpisodeInfoBundle(
    subjectId: Int,
    episodeId: Int,
    seriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Fallback,
    subjectCompleted: Boolean = false,
): SubjectEpisodeInfoBundle {
    return SubjectEpisodeInfoBundle(
        subjectId,
        episodeId,
        TestSubjectCollections[0].run {
            copy(subjectInfo = subjectInfo.copy(subjectId = subjectId))
        },
        TestSubjectCollections[0].episodes[0].run {
            copy(episodeInfo = episodeInfo.copy(episodeId = episodeId))
        },
        seriesInfo,
        subjectCompleted = subjectCompleted,
    )
}

class SubjectEpisodeInfoBundleLoader(
    subjectId: Int,
    episodeIdFlow: Flow<Int>,
    koin: Koin,
) {

    private val getSubjectEpisodeInfoBundleFlowUseCase: GetSubjectEpisodeInfoBundleFlowUseCase by koin.inject()

    private val restarter = FlowRestarter()
    private val flowLoadErrorObserver = FlowLoadErrorObserver()

    val infoLoadErrorState: StateFlow<LoadError?> = flowLoadErrorObserver.loadErrorState

    val infoBundleFlow: Flow<SubjectEpisodeInfoBundle?> =
        episodeIdFlow.map { GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId(subjectId, it) }
            .transformLatest { request ->

                emit(null)

                emitAll(
                    getSubjectEpisodeInfoBundleFlowUseCase(flowOf(request))
                        .catchLoadError(flowLoadErrorObserver),
                )
            }
            .restartable(restarter)

    fun restart() {
        restarter.restart()
    }
}
