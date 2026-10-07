package com.wynime.app.data.repository

import kotlinx.coroutines.flow.map
import com.wynime.app.data.network.BangumiCalendarRepository
import com.wynime.app.data.network.RecommendationRepository
import com.wynime.app.data.network.TrendsRepository
import com.wynime.app.data.persistent.dataStores
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.repository.episode.AnimeScheduleRepository
import com.wynime.app.data.repository.episode.BangumiCommentRepository
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.episode.EpisodeCommentRepository
import com.wynime.app.data.repository.episode.EpisodeProgressRepository
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.data.repository.media.EpisodePreferencesRepositoryImpl
import com.wynime.app.data.repository.media.MediaSourceInstanceRepository
import com.wynime.app.data.repository.media.MediaSourceInstanceRepositoryImpl
import com.wynime.app.data.repository.person.PersonCommentRepository
import com.wynime.app.data.repository.person.PersonDetailsRepository
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import com.wynime.app.data.repository.player.PlaybackHistorySyncer
import com.wynime.app.data.repository.subject.DefaultSubjectRelationsRepository
import com.wynime.app.data.repository.subject.FollowedSubjectsRepository
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.subject.SubjectCollectionRepositoryImpl
import com.wynime.app.data.repository.subject.BangumiTrackingMetadataRepository
import com.wynime.app.data.repository.subject.BangumiTrackingSyncEnqueuer
import com.wynime.app.data.repository.subject.BangumiTrackingSyncApi
import com.wynime.app.data.repository.subject.BangumiTrackingSyncApiImpl
import com.wynime.app.data.repository.subject.BangumiTrackingSyncRepository
import com.wynime.app.data.repository.subject.BangumiTrackingSyncSettingsStore
import com.wynime.app.data.repository.subject.BangumiSyncCoordinator
import com.wynime.app.data.repository.subject.SubjectRelationGraphRepository
import com.wynime.app.data.repository.subject.SubjectRelationsRepository
import com.wynime.app.data.repository.subject.SubjectSearchCompletionRepository
import com.wynime.app.data.repository.subject.SubjectSearchHistoryRepository
import com.wynime.app.data.repository.subject.SubjectSearchRepository
import com.wynime.app.data.repository.user.PreferencesRepositoryImpl
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.domain.foundation.get
import com.wynime.app.platform.Context
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module

private val Scope.database get() = get<WynimeDatabase>()
private val Scope.settingsRepository get() = get<SettingsRepository>()

@Suppress("UnusedReceiverParameter")
fun KoinApplication.repositoryModules(
    getContext: () -> Context,
) = module {
    single<UserRepository> {
        UserRepository(
            dataStore = getContext().dataStores.selfInfoStore,
            sessionStateProvider = get(),
            sessionManager = get(),
            officialBangumiApi = get(),
        )
    }

    single<TokenRepository> { TokenRepository(getContext().dataStores.tokenStore) }

    single<EpisodePreferencesRepository> {
        EpisodePreferencesRepositoryImpl(
            getContext().dataStores.preferredAllianceStore,
            database.preferredWebMediaSourceDao(),
        )
    }

    single<SubjectCollectionRepository> {
        SubjectCollectionRepositoryImpl(
            subjectService = get(),
            subjectCollectionDao = database.subjectCollection(),

            subjectRelationsDao = database.subjectRelations(),
            episodeCollectionRepository = get(),
            episodeService = get(),
            episodeCollectionDao = database.episodeCollection(),
            sessionManager = get(),
            nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode },
            getEpisodeTypeFiltersUseCase = get(),
            trackingMetadataRepository = get(),
            trackingSyncEnqueuer = get<BangumiTrackingSyncEnqueuer>(),
            trackingSyncSettingsStore = get(),
            syncCoordinator = get(),
        )
    }

    single { BangumiSyncCoordinator() }

    single<BangumiTrackingSyncSettingsStore> {
        BangumiTrackingSyncSettingsStore(getContext().dataStores.preferencesStore)
    }

    single<BangumiTrackingSyncApi> { BangumiTrackingSyncApiImpl(get()) }

    single {
        BangumiTrackingMetadataRepository(
            dao = database.bangumiTrackingMetadataDao(),
            tokenRepository = get(),
            accountBindingStore = get(),
        )
    }

    single<BangumiTrackingSyncRepository> {
        BangumiTrackingSyncRepository(
            subjectCollectionDao = database.subjectCollection(),
            metadataDao = database.bangumiTrackingMetadataDao(),
            metadataRepository = get(),
            subjectService = get(),
            bangumiApi = get(),
            settingsStore = get(),
            syncCoordinator = get(),
        )
    }

    single<BangumiTrackingSyncEnqueuer> { get<BangumiTrackingSyncRepository>() }

    single<FollowedSubjectsRepository> {
        FollowedSubjectsRepository(
            subjectCollectionRepository = get(),
            episodeCollectionRepository = get(),
            settingsRepository = get(),
            sessionManager = get(),
        )
    }

    single<SubjectSearchRepository> {
        SubjectSearchRepository(
            wynimeSubjectSearchService = get(),
            subjectCollectionRepository = get(),
        )
    }

    single<SubjectSearchCompletionRepository> {
        SubjectSearchCompletionRepository(
            wynimeSubjectSearchService = get(),
            subjectCollectionRepository = get(),
            settingsRepository = get(),
        )
    }

    single<SubjectSearchHistoryRepository> {
        SubjectSearchHistoryRepository(database.searchHistory(), database.searchTag())
    }

    single<SubjectRelationsRepository> {
        DefaultSubjectRelationsRepository(
            database.subjectCollection(),
            database.subjectRelations(),
            subjectService = get(),
            subjectCollectionRepository = get(),
            wynimeSubjectRelationIndexService = get(),
        )
    }
    single<SubjectRelationGraphRepository> {
        SubjectRelationGraphRepository(get(), database.subjectCollection())
    }

    single<PersonDetailsRepository> {
        PersonDetailsRepository(
            bangumiApi = get(),
        )
    }

    single<AnimeScheduleRepository> {
        AnimeScheduleRepository(
            calendarRepository = get(),
            bangumiScheduleService = get(),
        )
    }

    single<BangumiCommentRepository> {
        BangumiCommentRepository(
            get(),
            database.subjectReviews(),
        )
    }

    single<EpisodeCollectionRepository> {
        EpisodeCollectionRepository(
            subjectDao = database.subjectCollection(),
            episodeCollectionDao = database.episodeCollection(),
            episodeService = get(),
            subjectCollectionRepository = inject(),
            getEpisodeTypeFiltersUseCase = get(),
        )
    }

    single<EpisodeProgressRepository> {
        EpisodeProgressRepository(
            episodeCollectionRepository = get(),
            downloadManager = get(),
        )
    }

    single<EpisodeCommentRepository> { EpisodeCommentRepository(wynimeCommentService = get()) }

    single<PersonCommentRepository> { PersonCommentRepository(wynimeCommentService = get()) }

    single<MediaSourceInstanceRepository> {
        MediaSourceInstanceRepositoryImpl(getContext().dataStores.mediaSourceSaveStore)
    }

    single<EpisodePlayHistoryRepository> {
        EpisodePlayHistoryRepositoryImpl(
            dataStore = getContext().dataStores.episodeHistoryStore,
            playbackHistoryDao = database.playbackHistoryDao(),
            onDirtyChanged = { get<PlaybackHistorySyncer>().requestSync() },
        )
    }

    single<BangumiCalendarRepository> { BangumiCalendarRepository(dataSource = get()) }
    single<TrendsRepository> { TrendsRepository(dataSource = get()) }

    single<RecommendationRepository> {
        RecommendationRepository(
            dataSource = get(),
            trendsRepository = get(),
            settingsRepository = get(),
            calendarRepository = get(),
        )
    }

    single<SettingsRepository> { PreferencesRepositoryImpl(getContext().dataStores.preferencesStore) }

}
