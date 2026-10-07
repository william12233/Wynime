package com.wynime.app.domain.media.resolver

import kotlinx.coroutines.CoroutineScope
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.datasources.api.Media
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData

object TestUniversalMediaResolver : MediaResolver {
    override fun supports(media: Media): Boolean = true

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> =
        TestMediaDataProvider()
}

class TestMediaDataProvider(
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
) : MediaDataProvider<UriMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData {
        return UriMediaData("https://example.com")
    }
}
