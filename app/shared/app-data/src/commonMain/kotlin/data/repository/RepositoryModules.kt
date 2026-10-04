/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.network.AutoSkipRepository
import me.him188.ani.app.data.network.RecommendationRepository
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepositoryImpl
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepository
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepositoryImpl
import me.him188.ani.app.data.repository.person.PersonCommentRepository
import me.him188.ani.app.data.repository.person.PersonDetailsRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import me.him188.ani.app.data.repository.player.PlaybackHistorySyncer
import me.him188.ani.app.data.repository.subject.DefaultSubjectRelationsRepository
import me.him188.ani.app.data.repository.subject.FollowedSubjectsRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepositoryImpl
import me.him188.ani.app.data.repository.subject.SubjectRelationGraphRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchCompletionRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.platform.Context
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module

private val Scope.database get() = get<AniDatabase>()
private val Scope.settingsRepository get() = get<SettingsRepository>()

@Suppress("UnusedReceiverParameter")
fun KoinApplication.repositoryModules(
    getContext: () -> Context,
) = module {
    single<UserRepository> {
        UserRepository(
            dataStore = getContext().dataStores.selfInfoStore,
            sessionStateProvider = get(),
            userApi = null,
            authApi = null,
            profileApi = null,
            bangumiApi = null,
            oauthApi = null,
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
//            characterDao = database.character(),
//            characterActorDao = database.characterActor(),
//            personDao = database.person(),
//            subjectCharacterRelationDao = database.subjectCharacterRelation(),
//            subjectPersonRelationDao = database.subjectPersonRelation(),
            subjectRelationsDao = database.subjectRelations(),
            episodeCollectionRepository = get(),
            animeScheduleRepository = get(),
            episodeService = get(),
            episodeCollectionDao = database.episodeCollection(),
            sessionManager = get(),
            nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode },
            getEpisodeTypeFiltersUseCase = get(),
        )
    }

    single<FollowedSubjectsRepository> {
        FollowedSubjectsRepository(
            subjectCollectionRepository = get(),
            animeScheduleRepository = get(),
            episodeCollectionRepository = get(),
            settingsRepository = get(),
            sessionManager = get(),
        )
    }

    single<SubjectSearchRepository> {
        SubjectSearchRepository(
            aniSubjectSearchService = get(),
            subjectCollectionRepository = get(),
        )
    }

    single<SubjectSearchCompletionRepository> {
        SubjectSearchCompletionRepository(
            aniSubjectSearchService = get(),
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
            aniSubjectRelationIndexService = get(),
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

    single<AnimeScheduleRepository> { AnimeScheduleRepository(get()) }

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
            animeScheduleRepository = get(),
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

    single<EpisodeCommentRepository> { EpisodeCommentRepository(aniCommentService = get()) }

    single<PersonCommentRepository> { PersonCommentRepository(aniCommentService = get()) }

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

    single<TrendsRepository> { TrendsRepository() }

    single<RecommendationRepository> { RecommendationRepository() }

    single<AutoSkipRepository> { AutoSkipRepository() }

    single<SettingsRepository> { PreferencesRepositoryImpl(getContext().dataStores.preferencesStore) }

}
