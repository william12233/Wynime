package com.wynime.app.domain.media.cache

import com.wynime.app.domain.media.download.MediaDownloadManager

interface DeleteCacheUseCase {
    suspend operator fun invoke(cache: MediaCache)
}

class DeleteCacheUseCaseImpl(
    private val downloadManager: MediaDownloadManager,
) : DeleteCacheUseCase {
    override suspend fun invoke(cache: MediaCache) {
        downloadManager.deleteDownload(cache)
    }
}
