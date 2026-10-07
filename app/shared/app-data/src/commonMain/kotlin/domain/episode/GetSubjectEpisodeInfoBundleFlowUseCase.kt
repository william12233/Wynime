package com.wynime.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.domain.usecase.UseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

fun interface GetSubjectEpisodeInfoBundleFlowUseCase : UseCase {
    data class SubjectIdAndEpisodeId(
        val subjectId: Int,
        val episodeId: Int
    )

    operator fun invoke(idsFlow: Flow<SubjectIdAndEpisodeId>): Flow<SubjectEpisodeInfoBundle>
}

class GetSubjectEpisodeInfoBundleFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : GetSubjectEpisodeInfoBundleFlowUseCase, KoinComponent {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val getEpisodeCollectionInfoFlowUseCase: GetEpisodeCollectionInfoFlowUseCase by inject()

    override fun invoke(idsFlow: Flow<GetSubjectEpisodeInfoBundleFlowUseCase.SubjectIdAndEpisodeId>): Flow<SubjectEpisodeInfoBundle> {
        return idsFlow.flatMapLatest { (subjectId, episodeId) ->

            subjectCollectionRepository.subjectCollectionFlow(subjectId).flatMapLatest { subject ->
                getEpisodeCollectionInfoFlowUseCase(subjectId, episodeId).map { episodeCollectionInfo ->
                    SubjectEpisodeInfoBundle(
                        subjectId, episodeId,
                        subject,
                        episodeCollectionInfo,
                        seriesInfo = SubjectSeriesInfo.compute(subject),
                        subjectCompleted = EpisodeCollections.isSubjectCompleted(
                            subject.episodes.map { it.episodeInfo },
                            subject.recurrence,
                        ),
                    )
                }
            }
        }.flowOn(flowContext)
    }
}
