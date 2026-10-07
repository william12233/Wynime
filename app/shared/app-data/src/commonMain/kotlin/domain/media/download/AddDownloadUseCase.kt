package com.wynime.app.domain.media.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.resolver.toEpisodeMetadata
import com.wynime.app.domain.usecase.UseCase
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.CacheCreate
import com.wynime.utils.analytics.recordEvent
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn

interface AddDownloadUseCase : UseCase {

    suspend operator fun invoke(
        subject: SubjectInfo,
        episode: EpisodeInfo,
        media: Media,
        metadata: MediaCacheMetadata,
    ): MediaCache
}

class AddDownloadUseCaseImpl(
    private val downloadManager: MediaDownloadManager,
) : AddDownloadUseCase {
    override suspend fun invoke(
        subject: SubjectInfo,
        episode: EpisodeInfo,
        media: Media,
        metadata: MediaCacheMetadata,
    ): MediaCache {
        require(metadata.subjectId == subject.subjectId.toString()) { "metadata.subjectId does not match subject" }
        require(metadata.episodeId == episode.episodeId.toString()) { "metadata.episodeId does not match episode" }
        val cache = downloadManager.createDownload(media, metadata, episode.toEpisodeMetadata())

        downloadManager.backgroundScope.launch {
            try {
                Analytics.recordEvent(CacheCreate) {
                    put("subject_id", subject.subjectId)
                    put("episode_id", episode.episodeId)
                    put(
                        "media_source_name",
                        when (media.kind) {
                            MediaSourceKind.WEB -> "web"
                            MediaSourceKind.LocalCache -> null
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to record analytics for download ${cache.cacheId}" }
            }
        }
        return cache
    }

    private companion object {
        private val logger = logger<AddDownloadUseCaseImpl>()
    }
}
