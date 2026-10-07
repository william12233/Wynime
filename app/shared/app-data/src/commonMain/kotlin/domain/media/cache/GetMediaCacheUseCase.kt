package com.wynime.app.domain.media.cache

import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.usecase.UseCase

interface GetMediaCacheUseCase : UseCase {
    suspend operator fun invoke(subjectId: Int, episodeId: Int): List<MediaCache>
}

class GetMediaCacheUseCaseImpl(
    private val downloadManager: MediaDownloadManager,
) : GetMediaCacheUseCase {
    override suspend fun invoke(subjectId: Int, episodeId: Int): List<MediaCache> {
        val subjectKey = subjectId.toString()
        val episodeKey = episodeId.toString()
        return downloadManager.findCaches { cache ->
            cache.metadata.subjectId == subjectKey && cache.metadata.episodeId == episodeKey
        }
    }
}
