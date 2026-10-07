@file:Suppress("DEPRECATION")

package com.wynime.app.domain.media.selector.legacy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.domain.media.createTestDefaultMedia
import com.wynime.app.domain.media.createTestMediaProperties
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaFetcher
import com.wynime.app.domain.media.fetch.MediaFetcherConfig
import com.wynime.app.domain.media.fetch.MediaSourceMediaFetcher
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.media.selector.MediaSelectorSubtitlePreferences
import com.wynime.app.domain.mediasource.instance.MediaSourceInstance
import com.wynime.app.domain.mediasource.instance.createTestMediaSourceInstance
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.paging.SinglePagePagedSource
import com.wynime.datasources.api.source.MatchKind
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaMatch
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.source.TestHttpMediaSource
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.Resolution
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.SubtitleLanguage
import kotlin.coroutines.ContinuationInterceptor

@Deprecated(MediaSelectorDeprecationMessage)
class MediaSelectorTestBuilder(
    private val testScope: TestScope,
) {
    val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Companion.Default)

    val mediaSources = mutableListOf<MediaSourceInstance>()

    fun delayedMediaSource(
        mediaSourceId: String,
        kind: MediaSourceKind = MediaSourceKind.WEB,
        enabled: Boolean = true,
    ): CompletableDeferred<List<Media>> {
        val deferred = CompletableDeferred<List<Media>>()
        mediaSources.add(
            createTestMediaSourceInstance(
                TestHttpMediaSource(
                    mediaSourceId = mediaSourceId,
                    kind = kind,
                    fetch = {
                        SinglePagePagedSource {
                            deferred.await().map { MediaMatch(it, MatchKind.EXACT) }.asFlow()
                        }
                    },
                ),
                isEnabled = enabled,
            ),
        )
        return deferred
    }

    fun createMedia(
        mediaSourceId: String,
        kind: MediaSourceKind = MediaSourceKind.WEB,
        alliance: String = "XX字幕组",
    ): DefaultMedia = createTestDefaultMedia(
        mediaId = "$mediaSourceId.1",
        mediaSourceId = mediaSourceId,
        originalTitle = "[XX字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.HttpStreamingFile("https://example.com/1.m3u8"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.Companion.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(
                SubtitleLanguage.ChineseSimplified,
                SubtitleLanguage.ChineseTraditional,
            ).map { it.id },
            resolution = "1080P",
            alliance = alliance,
            size = 122.megaBytes,
            subtitleKind = SubtitleKind.CLOSED,
        ),
        kind = kind,
        location = MediaSourceLocation.Online,
    )

    fun createMediaFetcher() = MediaSourceMediaFetcher(
        configProvider = { MediaFetcherConfig.Companion.Default },
        mediaSources = mediaSources,
        flowContext = testScope.coroutineContext[ContinuationInterceptor.Key]!!,
    )

    fun createMediaFetchSession(fetcher: MediaFetcher) = fetcher.newSession(
        MediaFetchRequest(
            subjectId = "1",
            episodeId = "1",
            subjectNames = listOf("孤独摇滚"),
            episodeSort = EpisodeSort(1),
            episodeName = "test",
        ),
    )

    fun createMediaSelector(fetchSession: MediaFetchSession) = DefaultMediaSelector(
        mediaSelectorContextNotCached = fetchSession.request.map { createMediaSelectorContext(it) },
        mediaListNotCached = fetchSession.cumulativeResults,
        savedUserPreference = savedUserPreference,
        savedDefaultPreference = savedDefaultPreference,
        enableCaching = false,
        mediaSelectorSettings = mediaSelectorSettings,
        flowCoroutineContext = testScope.coroutineContext[ContinuationInterceptor.Key]!!,
    )

    private fun createMediaSelectorContext(fetchRequest: MediaFetchRequest): MediaSelectorContext {
        return MediaSelectorContext(
            subjectFinished = false,
            mediaSourcePrecedence = emptyList(),
            subtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSeriesInfo = SubjectSeriesInfo.Fallback,
            subjectInfo = SubjectInfo.Empty.copy(
                subjectId = fetchRequest.subjectId.toInt(),
                name = fetchRequest.subjectNames.firstOrNull() ?: "",
                nameCn = fetchRequest.subjectNameCN ?: "",
            ),
            episodeInfo = EpisodeInfo.Empty.copy(
                episodeId = fetchRequest.episodeId.toInt(),
                name = fetchRequest.episodeName,
                sort = fetchRequest.episodeSort,
                ep = fetchRequest.episodeEp,

                ),
            mediaSourceTiers = MediaSelectorSourceTiers.Empty,
        )
    }

    fun create(): Triple<MediaSourceMediaFetcher, MediaFetchSession, DefaultMediaSelector> {
        val fetcher = createMediaFetcher()
        val session = createMediaFetchSession(fetcher)
        return Triple(
            fetcher,
            session,
            createMediaSelector(session),
        )
    }

    companion object {
        val DEFAULT_PREFERENCE = MediaPreference.Companion.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.Companion.R2160P,
                Resolution.Companion.R1440P,
                Resolution.Companion.R1080P,
                Resolution.Companion.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                SubtitleLanguage.ChineseSimplified,
                SubtitleLanguage.ChineseTraditional,
            ).map { it.id },
        )
    }
}
