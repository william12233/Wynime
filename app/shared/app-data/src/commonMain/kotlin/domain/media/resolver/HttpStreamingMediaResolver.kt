package com.wynime.app.domain.media.resolver

import kotlinx.coroutines.CoroutineScope
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.ResourceLocation
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData

class HttpStreamingMediaResolver : MediaResolver {
    override fun supports(media: Media): Boolean {
        return media.download is ResourceLocation.HttpStreamingFile
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        if (!supports(media)) throw UnsupportedMediaException(media)
        return HttpStreamingMediaDataProvider(
            media.download.uri,
            media.originalTitle,
            emptyMap(),
            media.extraFiles.toMediampMediaExtraFiles(),
        )
    }
}

class HttpStreamingMediaDataProvider(
    val uri: String,
    val originalTitle: String,
    private val headers: Map<String, String> = emptyMap(),
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
) : MediaDataProvider<UriMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData = UriMediaData(uri, headers, extraFiles)
    override fun toString(): String = "HttpStreamingVideoSource(uri='$uri')"
}

