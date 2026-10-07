package com.wynime.app.domain.sourceplugin

import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.app.domain.media.resolver.DownloadMediaResolver
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.domain.media.resolver.HttpStreamingMediaDataProvider
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.resolver.UnsupportedMediaException
import com.wynime.app.domain.media.resolver.toMediampMediaExtraFiles
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceResultStatus
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceTracePhase
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.Uuid
import kotlinx.coroutines.CancellationException

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
        } catch (error: CancellationException) {
            throw error
        } catch (error: LinkageError) {
            val failure = sourcePluginBoundaryFailure(
                traceId = traceId,
                provider = reference.pluginId,
                entryPoint = phase.name,
                fallbackStatus = SourceResultStatus.RESOLVE_ERROR,
                error = error,
                url = reference.uri,
                retryable = false,
            )
            trace(traceId, reference, phase, failure.status, failure.message)
            throw failure
        } catch (error: ClassCastException) {
            val failure = sourcePluginBoundaryFailure(
                traceId = traceId,
                provider = reference.pluginId,
                entryPoint = phase.name,
                fallbackStatus = SourceResultStatus.RESOLVE_ERROR,
                error = error,
                url = reference.uri,
                retryable = false,
            )
            trace(traceId, reference, phase, failure.status, failure.message)
            throw failure
        } catch (error: Error) {
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
