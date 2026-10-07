package com.wynime.app.domain.usecase

import com.wynime.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import com.wynime.app.data.repository.subject.GetEpisodeTypeFiltersUseCaseImpl
import com.wynime.app.domain.comment.PostCommentUseCase
import com.wynime.app.domain.comment.PostCommentUseCaseImpl
import com.wynime.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import com.wynime.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCaseImpl
import com.wynime.app.domain.episode.GetAnimeScheduleFlowUseCase
import com.wynime.app.domain.episode.GetAnimeScheduleFlowUseCaseImpl
import com.wynime.app.domain.episode.GetAnimeSeasonIdsFlowUseCase
import com.wynime.app.domain.episode.GetAnimeSeasonIdsFlowUseCaseImpl
import com.wynime.app.domain.episode.GetEpisodeCollectionInfoFlowUseCase
import com.wynime.app.domain.episode.GetEpisodeCollectionInfoFlowUseCaseImpl
import com.wynime.app.domain.episode.GetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.episode.GetEpisodeCollectionTypeUseCaseImpl
import com.wynime.app.domain.episode.GetSubjectEpisodeInfoBundleFlowUseCase
import com.wynime.app.domain.episode.GetSubjectEpisodeInfoBundleFlowUseCaseImpl
import com.wynime.app.domain.episode.GetSubjectRecommendationUseCase
import com.wynime.app.domain.episode.GetSubjectRecommendationUseCaseImpl
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCaseImpl
import com.wynime.app.domain.media.cache.DeleteCacheUseCase
import com.wynime.app.domain.media.cache.DeleteCacheUseCaseImpl
import com.wynime.app.domain.media.cache.GetMediaCacheUseCase
import com.wynime.app.domain.media.cache.GetMediaCacheUseCaseImpl
import com.wynime.app.domain.media.download.AddDownloadUseCase
import com.wynime.app.domain.media.download.AddDownloadUseCaseImpl
import com.wynime.app.domain.media.download.DownloadRequestSessionFactory
import com.wynime.app.domain.media.fetch.SubjectMediaFetchSessionRegistry
import com.wynime.app.domain.media.selector.GetPreferredMediaSourceSortingUseCase
import com.wynime.app.domain.media.selector.GetPreferredMediaSourceSortingUseCaseImpl
import com.wynime.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import com.wynime.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import com.wynime.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import com.wynime.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCaseImpl
import com.wynime.app.domain.media.selector.MediaSelectorFactory
import com.wynime.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import com.wynime.app.domain.mediasource.GetMediaSelectorSourceTiersUseCaseImpl
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCaseImpl
import com.wynime.app.domain.mediasource.SetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.mediasource.SetPreferredWebMediaSourceUseCaseImpl
import com.wynime.app.domain.mediasource.instance.GetMediaSourceInstancesUseCase
import com.wynime.app.domain.mediasource.instance.GetMediaSourceInstancesUseCaseImpl
import com.wynime.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import com.wynime.app.domain.settings.GetMediaSelectorSettingsFlowUseCaseImpl
import com.wynime.app.domain.settings.GetMediaSelectorSettingsUseCase
import com.wynime.app.domain.settings.GetMediaSelectorSettingsUseCaseImpl
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCaseImpl
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

fun KoinApplication.useCaseModules() = module {
    single<GetEpisodeCollectionInfoFlowUseCase> { GetEpisodeCollectionInfoFlowUseCaseImpl() }
    single<MediaSelectorAutoSelectUseCase> { MediaSelectorAutoSelectUseCaseImpl() }
    single<MediaSelectorEventSavePreferenceUseCase> { MediaSelectorEventSavePreferenceUseCaseImpl }
    single<GetSubjectEpisodeInfoBundleFlowUseCase> { GetSubjectEpisodeInfoBundleFlowUseCaseImpl() }
    single<CreateMediaFetchSelectBundleFlowUseCase> { CreateMediaFetchSelectBundleFlowUseCaseImpl() }
    single<GetMediaSelectorSettingsFlowUseCase> { GetMediaSelectorSettingsFlowUseCaseImpl }
    single<GetVideoScaffoldConfigUseCase> { GetVideoScaffoldConfigUseCaseImpl }
    single<SetEpisodeCollectionTypeUseCase> { SetEpisodeCollectionTypeUseCaseImpl(koin) }
    single<GetEpisodeCollectionTypeUseCase> { GetEpisodeCollectionTypeUseCaseImpl(koin) }
    single<GetAnimeScheduleFlowUseCase> { GetAnimeScheduleFlowUseCaseImpl(get()) }
    single<GetAnimeSeasonIdsFlowUseCase> { GetAnimeSeasonIdsFlowUseCaseImpl(get()) }

    single<PostCommentUseCase> { PostCommentUseCaseImpl(get(), get()) }
    single<GetPreferredMediaSourceSortingUseCase> { GetPreferredMediaSourceSortingUseCaseImpl(get()) }
    single<GetMediaSelectorSourceTiersUseCase> { GetMediaSelectorSourceTiersUseCaseImpl(get()) }
    single<GetEpisodeTypeFiltersUseCase> { GetEpisodeTypeFiltersUseCaseImpl(get()) }
    single<GetMediaSelectorSettingsUseCase> { GetMediaSelectorSettingsUseCaseImpl(get()) }
    single<GetMediaSourceInstancesUseCase> { GetMediaSourceInstancesUseCaseImpl(get()) }
    single<GetSubjectRecommendationUseCase> { GetSubjectRecommendationUseCaseImpl(get()) }
    single<GetMediaCacheUseCase> { GetMediaCacheUseCaseImpl(get()) }
    single<DeleteCacheUseCase> { DeleteCacheUseCaseImpl(get()) }
    single<AddDownloadUseCase> {
        AddDownloadUseCaseImpl(get())
    }
    single {
        DownloadRequestSessionFactory(
            get(), get(), get(), MediaSelectorFactory.withKoin(koin), get(), get(),
            get<SubjectMediaFetchSessionRegistry>(),
        )
    }
    single<GetPreferredWebMediaSourceUseCase> { GetPreferredWebMediaSourceUseCaseImpl(get()) }
    single<SetPreferredWebMediaSourceUseCase> { SetPreferredWebMediaSourceUseCaseImpl(get()) }
}

val GlobalKoin get() = KoinPlatform.getKoin()
