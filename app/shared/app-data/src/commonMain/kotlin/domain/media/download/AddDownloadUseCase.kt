/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.CacheCreate
import me.him188.ani.utils.analytics.recordEvent
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

/**
 * 把用户为某一集选定的资源保存为下载.
 */
interface AddDownloadUseCase : UseCase {
    /**
     * 持久化成功即返回记录; 統計事件在后台进行, 失败不影响结果. [metadata] 必须属于 [subject] 与 [episode].
     */
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

        // 后台统计工作失败不影响下载记录.
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
