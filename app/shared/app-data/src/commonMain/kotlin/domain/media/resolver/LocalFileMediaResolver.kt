package com.wynime.app.domain.media.resolver

import kotlinx.io.files.Path
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.app.domain.media.player.data.SystemFileMediaDataProvider
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.ResourceLocation

class LocalFileMediaResolver : MediaResolver {
    override fun supports(media: Media): Boolean {
        return media.download is ResourceLocation.LocalFile
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        when (val download = media.download) {
            is ResourceLocation.LocalFile -> {
                return SystemFileMediaDataProvider(
                    Path(download.filePath),
                    media.extraFiles.toMediampMediaExtraFiles(),
                    fileType = download.fileType,
                )
            }

            else -> throw UnsupportedMediaException(media)
        }
    }
}