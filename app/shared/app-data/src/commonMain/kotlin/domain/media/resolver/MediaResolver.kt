package com.wynime.app.domain.media.resolver

import androidx.compose.runtime.Composable
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import org.openani.mediamp.source.MediaData
import kotlin.coroutines.cancellation.CancellationException

interface MediaResolver {

    fun supports(media: Media): Boolean

    @Composable
    fun ComposeContent() {
    }

    @Throws(MediaResolutionException::class, CancellationException::class)
    suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<MediaData>

    companion object {
        fun from(vararg resolvers: MediaResolver): MediaResolver {
            return ChainedMediaResolver(resolvers.toList())
        }

        fun from(resolvers: Iterable<MediaResolver>): MediaResolver {
            return ChainedMediaResolver(resolvers.toList())
        }
    }
}

interface DownloadMediaResolver {
    suspend fun resolveForDownload(media: Media, episode: EpisodeMetadata): MediaDataProvider<MediaData>
}

data class EpisodeMetadata(
    val title: String,
    val ep: EpisodeSort?,
    val sort: EpisodeSort,
)

fun EpisodeInfo.toEpisodeMetadata(): EpisodeMetadata {
    return EpisodeMetadata(nameCn, ep, sort)
}

class UnsupportedMediaException(
    val media: Media,
) : UnsupportedOperationException("Media is not supported: $media")

enum class ResolutionFailures {

    FETCH_TIMEOUT,

    NETWORK_ERROR,

    NO_MATCHING_RESOURCE,

    ENGINE_ERROR,
}

class MediaResolutionException(
    val reason: ResolutionFailures,
    override val cause: Throwable? = null,
) : Exception("Failed to resolve video source: $reason", cause)

private class ChainedMediaResolver(
    private val resolvers: List<MediaResolver>
) : MediaResolver, DownloadMediaResolver {
    override fun supports(media: Media): Boolean {
        return resolvers.any { it.supports(media) }
    }

    @Composable
    override fun ComposeContent() {
        this.resolvers.forEach {
            it.ComposeContent()
        }
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        return resolvers.firstOrNull { it.supports(media) }?.resolve(media, episode)
            ?: throw UnsupportedMediaException(media)
    }

    override suspend fun resolveForDownload(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        val resolver = resolvers.firstOrNull { it.supports(media) }
            ?: throw UnsupportedMediaException(media)
        return (resolver as? DownloadMediaResolver)?.resolveForDownload(media, episode)
            ?: resolver.resolve(media, episode)
    }
}
