package com.wynime.app.domain.episode

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.domain.foundation.LoadError
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import kotlin.time.Duration.Companion.seconds

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
    private val loadError = MutableStateFlow<LoadError?>(null)

    val infoLoadErrorState: StateFlow<LoadError?> = loadError.asStateFlow()

    val infoBundleFlow: Flow<SubjectEpisodeInfoBundle?> =
        episodeIdFlow.map { GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId(subjectId, it) }
            .transformLatest { request ->

                loadError.value = null
                emit(null)
                loadInfo(request).catch { error ->
                    loadError.value = LoadError.fromException(RepositoryException.wrapOrThrowCancellation(error))
                }.collect { emit(it) }
            }
            .restartable(restarter)

    private fun loadInfo(request: GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId) = flow {
        coroutineScope {
            val bundles = getSubjectEpisodeInfoBundleFlowUseCase(flowOf(request)).produceIn(this)
            try {
                val first = withTimeoutOrNull(15.seconds) { bundles.receiveCatching() }
                    ?: throw RepositoryNetworkException("Episode metadata timed out")
                first.exceptionOrNull()?.let { throw it }
                val bundle = first.getOrNull()
                if (bundle == null) {
                    loadError.value = LoadError.NoResults
                    return@coroutineScope
                }
                emit(bundle)
                for (update in bundles) emit(update)
            } finally {
                bundles.cancel()
            }
        }
    }

    fun restart() {
        restarter.restart()
    }
}
