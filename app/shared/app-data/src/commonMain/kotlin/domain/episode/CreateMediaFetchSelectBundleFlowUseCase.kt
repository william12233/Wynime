package com.wynime.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.fetch.create
import com.wynime.app.domain.media.fetch.createFetchFetchSession
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContextFlowProducer
import com.wynime.app.domain.usecase.UseCase
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.collections.tupleOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import com.wynime.app.domain.media.fetch.withRequestedNumbers

fun interface CreateMediaFetchSelectBundleFlowUseCase : UseCase {

    operator fun invoke(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>,
    ): Flow<MediaFetchSelectBundle?>

    operator fun invoke(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>,
        fetchSessions: SubjectMediaFetchSessions,
    ): Flow<MediaFetchSelectBundle?> = invoke(subjectEpisodeInfoBundleFlow)
}

class CreateMediaFetchSelectBundleFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : CreateMediaFetchSelectBundleFlowUseCase, KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    override fun invoke(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>
    ): Flow<MediaFetchSelectBundle?> = createBundleFlow(subjectEpisodeInfoBundleFlow) { request ->
        mediaSourceManager.createFetchFetchSession(flowOf(request))
    }

    override fun invoke(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>,
        fetchSessions: SubjectMediaFetchSessions,
    ): Flow<MediaFetchSelectBundle?> = createBundleFlow(subjectEpisodeInfoBundleFlow) { request ->
        fetchSessions.get(request)
    }

    private fun createBundleFlow(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>,
        createFetchSession: suspend (MediaFetchRequest) -> MediaFetchSession,
    ): Flow<MediaFetchSelectBundle?> {
        val bundleDistinct = subjectEpisodeInfoBundleFlow
            .distinctUntilChangedBy { bundle ->
                if (bundle == null) {
                    null
                } else {

                    tupleOf(
                        bundle.subjectInfo.subjectId,
                        bundle.episodeInfo.episodeId,

                        bundle.subjectCollectionInfo.subjectInfo.nameCn,
                        bundle.subjectCollectionInfo.subjectInfo.name,
                        bundle.subjectCollectionInfo.subjectInfo.allNames,

                        bundle.episodeInfo.sort,
                        bundle.episodeInfo.ep,

                        bundle.episodeInfo.name,
                        bundle.episodeInfo.nameCn,

                        bundle.seriesInfo,
                        bundle.subjectCompleted,

                        bundle.subjectCollectionInfo.episodes.map { it.episodeId },
                    )
                }
            }

        val fetchRequestFlow: Flow<MediaFetchSession?> = bundleDistinct

            .filterNotNull()
            .map { bundle ->
                MediaFetchRequest.create(
                    bundle.subjectCollectionInfo.subjectInfo,
                    bundle.episodeCollectionInfo.episodeInfo,
                    episodes = bundle.subjectCollectionInfo.episodes.map { it.episodeInfo },
                )
            }
            .distinctUntilChanged()
            .mapLatest { req ->
                logger.info { "MediaFetchRequest changed. Creating MediaFetchSession for reqeust: $req" }
                createFetchSession(req)
            }
            .onStart<MediaFetchSession?> { emit(null) }

        return combine(bundleDistinct, fetchRequestFlow) { bundle, fetchSession ->
            tupleOf(bundle, fetchSession)
        }.mapLatest { (bundle, fetchSession) ->
            bundle ?: return@mapLatest null
            fetchSession ?: return@mapLatest null

            val selector = DefaultMediaSelector(
                MediaSelectorContextFlowProducer(

                    flowOf(bundle.subjectCompleted ?: false),
                    mediaSourceManager.allInstances.map { list ->
                        list.map { it.mediaSourceId }
                    },
                    flowOf(bundle.seriesInfo ?: SubjectSeriesInfo.Fallback),
                    flowOf(bundle.subjectInfo),
                    fetchSession.latestRequest.map { bundle.episodeInfo.withRequestedNumbers(it) },
                    mediaSourceManager.mediaSourceTiersFlow(),
                ).flow,
                fetchSession.cumulativeResults,
                savedUserPreference = episodePreferencesRepository.mediaPreferenceFlow(bundle.subjectId),
                savedDefaultPreference = settingsRepository.defaultMediaPreference.flow,
                mediaSelectorSettings = settingsRepository.mediaSelectorSettings.flow,
                flowCoroutineContext = flowContext,
            )

            MediaFetchSelectBundle(
                fetchSession,
                selector,
            )
        }.flowOn(flowContext)
    }

    private companion object {
        private val logger = logger<CreateMediaFetchSelectBundleFlowUseCaseImpl>()
    }
}
