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
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaDataProvider
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.resolver.UnsupportedMediaException
import me.him188.ani.app.domain.media.resolver.toMediampMediaExtraFiles
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.source.plugin.api.ResolvedMediaFormat
import me.him188.ani.source.plugin.api.SourceResolveRequest

class SourcePluginMediaResolver(
    private val registry: SourcePluginRegistry,
    private val webResolver: MediaResolver,
) : MediaResolver {
    override fun supports(media: Media): Boolean = media.download is ResourceLocation.SourcePluginMedia

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        val reference = media.download as? ResourceLocation.SourcePluginMedia
            ?: throw UnsupportedMediaException(media)
        val resolved = registry.resolve(
            SourceResolveRequest(
                subjectId = reference.subjectId,
                channelId = reference.channelId,
                episodeId = reference.episodeId,
                episodeSort = episode.sort.number,
                episodeEp = episode.ep?.toString(),
                pluginId = reference.pluginId,
            ),
        )
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
