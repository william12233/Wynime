/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.media.resolver.DownloadMediaResolver
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaDataProvider
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.UnsupportedMediaException
import me.him188.ani.app.domain.media.resolver.toMediampMediaExtraFiles
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.source.plugin.api.ResolvedMediaFormat
import me.him188.ani.source.plugin.api.SourceResultStatus
import me.him188.ani.source.plugin.api.SourceResolveRequest
import me.him188.ani.source.plugin.api.SourceTracePhase
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Uuid

class SourcePluginMediaResolver(
    private val registry: SourcePluginRegistry,
    private val webResolver: MediaResolver,
) : MediaResolver, DownloadMediaResolver {
    private val logger = logger<SourcePluginMediaResolver>()

    override fun supports(media: Media): Boolean = media.download is ResourceLocation.SourcePluginMedia

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        return resolveInternal(media, episode, SourceTracePhase.PLAY_RESOLVE)
    }

    override suspend fun resolveForDownload(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        return resolveInternal(media, episode, SourceTracePhase.DOWNLOAD_RESOLVE)
    }

    private suspend fun resolveInternal(
        media: Media,
        episode: EpisodeMetadata,
        phase: SourceTracePhase,
    ): MediaDataProvider<*> {
        val reference = media.download as? ResourceLocation.SourcePluginMedia
            ?: throw UnsupportedMediaException(media)
        val traceId = reference.traceId.ifBlank { Uuid.randomString() }
        val resolved = try {
            registry.resolve(
                SourceResolveRequest(
                    subjectId = reference.subjectId,
                    channelId = reference.channelId,
                    episodeId = reference.episodeId,
                    episodeSort = episode.sort.number,
                    episodeEp = episode.ep?.toString(),
                    pluginId = reference.pluginId,
                    traceId = traceId,
                    entryPoint = phase.name,
                ),
            )
        } catch (error: SourcePluginFailure) {
            trace(traceId, reference, phase, error.status, error.message)
            throw error
        } catch (error: Throwable) {
            val failure = SourcePluginFailure(
                status = SourceResultStatus.RESOLVE_ERROR,
                diagnostics = sourceFailureDiagnostics(
                    traceId = traceId,
                    provider = reference.pluginId,
                    entryPoint = phase.name,
                    status = SourceResultStatus.RESOLVE_ERROR,
                    url = reference.uri,
                    failureReason = error::class.simpleName,
                ),
                retryable = true,
                cause = error,
            )
            trace(traceId, reference, phase, failure.status, failure.message)
            throw failure
        }
        trace(traceId, reference, phase, SourceResultStatus.SUCCESS)
        val scheme = resolved.url.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") {
            val failure = SourcePluginFailure(
                status = SourceResultStatus.MEDIA_UNREACHABLE,
                diagnostics = sourceFailureDiagnostics(
                    traceId = traceId,
                    provider = reference.pluginId,
                    entryPoint = SourceTracePhase.FINAL_MEDIA_CHECK.name,
                    status = SourceResultStatus.MEDIA_UNREACHABLE,
                    url = resolved.url,
                    failureReason = "unsupported media URL scheme",
                ),
                retryable = false,
            )
            trace(traceId, reference, SourceTracePhase.FINAL_MEDIA_CHECK, failure.status, failure.message)
            throw failure
        }
        trace(traceId, reference, SourceTracePhase.FINAL_MEDIA_CHECK, SourceResultStatus.SUCCESS, resolved.format.name)
        return when (resolved.format) {
            ResolvedMediaFormat.WEB -> webResolver.resolve(
                media.toWebMedia(resolved.url, resolved.requestHeaders()),
                episode,
            )

            ResolvedMediaFormat.HLS,
            ResolvedMediaFormat.MP4,
            ResolvedMediaFormat.UNKNOWN,
            -> HttpStreamingMediaDataProvider(
                uri = resolved.url,
                originalTitle = media.originalTitle,
                headers = resolved.requestHeaders(),
                extraFiles = media.extraFiles.toMediampMediaExtraFiles(),
            )
        }
    }

    private fun trace(
        traceId: String,
        reference: ResourceLocation.SourcePluginMedia,
        phase: SourceTracePhase,
        status: SourceResultStatus,
        detail: String? = null,
    ) {
        logger.info {
            "source_trace phase=${phase.name} traceId=$traceId provider=${reference.pluginId} " +
                "status=${status.name} url=${safeSourceUrl(reference.uri)} detail=${detail.orEmpty()}"
        }
    }

    private fun Media.toWebMedia(pageUrl: String, headers: Map<String, String>): Media = DefaultMedia(
        mediaId = mediaId,
        mediaSourceId = mediaSourceId,
        originalUrl = originalUrl,
        download = ResourceLocation.WebVideo(pageUrl, headers),
        originalTitle = originalTitle,
        publishedTime = publishedTime,
        properties = properties,
        episodeRange = episodeRange,
        extraFiles = extraFiles,
        location = location,
        kind = kind,
    )
}
