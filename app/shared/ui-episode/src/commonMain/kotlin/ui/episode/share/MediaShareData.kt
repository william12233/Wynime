package com.wynime.app.ui.episode.share

import androidx.compose.runtime.Immutable
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.ResourceLocation
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.UriMediaData

@Immutable
data class MediaShareData(
    val websiteUrl: String?,
    val download: ResourceLocation?
) {
    companion object {
        fun from(
            media: Media?,
            mediaData: MediaData?,
        ): MediaShareData {
            val realDownload = when (mediaData) {
                is UriMediaData -> {
                    ResourceLocation.HttpStreamingFile(mediaData.uri)
                }

                is SeekableInputMediaData,
                null -> {
                    media?.download
                }
            }

            return MediaShareData(
                websiteUrl = media?.originalUrl,
                download = realDownload,
            )
        }
    }
}
