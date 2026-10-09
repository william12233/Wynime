package com.wynime.app.domain.media.selector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.subject.SubjectRelationsRepository
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.selector.MediaSelectorFactory.Companion.withRepositories
import com.wynime.datasources.api.Media
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.combine
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.withRequestedNumbers
import com.wynime.datasources.api.source.MediaFetchRequest

interface MediaSelectorFactory {

    fun create(
        subjectId: Int,
        episodeId: Int,
        mediaList: Flow<List<Media>>,
        flowCoroutineContext: CoroutineContext = Dispatchers.Default,
        fetchRequest: Flow<MediaFetchRequest>? = null,
    ): MediaSelector

    companion object {
        fun withKoin(koin: Koin = KoinPlatform.getKoin()): MediaSelectorFactory = withRepositories(
            episodePreferencesRepository = koin.get(),
            settingsRepository = koin.get(),
            episodeCollectionRepository = koin.get(),
            mediaSourceManager = koin.get(),
            subjectRelationsRepository = koin.get(),
            subjectCollectionRepository = koin.get(),
        )

        fun withRepositories(
            episodePreferencesRepository: EpisodePreferencesRepository,
            settingsRepository: SettingsRepository,
            episodeCollectionRepository: EpisodeCollectionRepository,
            mediaSourceManager: MediaSourceManager,
            subjectRelationsRepository: SubjectRelationsRepository,
            subtitlePreferences: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.CurrentPlatform,
            subjectCollectionRepository: SubjectCollectionRepository,
        ): MediaSelectorFactory = object : MediaSelectorFactory {
            override fun create(
                subjectId: Int,
                episodeId: Int,
                mediaList: Flow<List<Media>>,
                flowCoroutineContext: CoroutineContext,
                fetchRequest: Flow<MediaFetchRequest>?,
            ): MediaSelector {
                val subjectInfoFlow = subjectCollectionRepository
                    .subjectCollectionFlow(subjectId)
                    .map { it.subjectInfo }
                val subjectInfoWithRequestFlow = fetchRequest?.let { requestFlow ->
                    combine(subjectInfoFlow, requestFlow) { subjectInfo, request ->
                        subjectInfo.withFetchRequestSubjectNames(request)
                    }
                } ?: subjectInfoFlow
                return DefaultMediaSelector(
                    MediaSelectorContextFlowProducer(
                        episodeCollectionRepository.subjectCompletedFlow(subjectId),
                        mediaSourceManager.allInstances.map { list ->
                            list.map { it.mediaSourceId }
                        },
                        subjectRelationsRepository.subjectSeriesInfoFlow(subjectId),
                        subjectInfoWithRequestFlow,
                        episodeCollectionRepository.episodeCollectionInfoFlow(subjectId, episodeId).map { it.episodeInfo }.let { episodeInfo ->
                            if (fetchRequest == null) episodeInfo else combine(episodeInfo, fetchRequest) { info, request -> info.withRequestedNumbers(request) }
                        },
                        mediaSourceManager.mediaSourceTiersFlow(),
                        flowOf(subtitlePreferences),
                    ).flow,
                    mediaList,
                    savedUserPreference = episodePreferencesRepository.mediaPreferenceFlow(subjectId),
                    savedDefaultPreference = settingsRepository.defaultMediaPreference.flow,
                    mediaSelectorSettings = settingsRepository.mediaSelectorSettings.flow,
                    flowCoroutineContext = flowCoroutineContext,
                )
            }
        }
    }
}
