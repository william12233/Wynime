package com.wynime.app.domain.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wynime.app.domain.media.player.data.DownloadingMediaData
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.utils.coroutines.sampleWithInitial
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.NetworkStats
import org.openani.mediamp.source.UriMediaData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val PLAYER_DOWNLOAD_SPEED_SAMPLE_PERIOD: Duration = 1.seconds

@OptIn(ExperimentalMediampApi::class)
fun MediampPlayer.downloadSpeedFlow(): Flow<FileSize> = mediaData.flatMapLatest { data ->
    when (data) {
        is DownloadingMediaData -> data.networkStats.map { it.downloadSpeed.toFileSizeOrUnspecified() }
        is UriMediaData -> features[NetworkStats]?.downloadSpeedBytesPerSecond
            ?.sampleWithInitial(PLAYER_DOWNLOAD_SPEED_SAMPLE_PERIOD)
            ?.map { it.toFileSizeOrUnspecified() }
            ?: flowOf(FileSize.Unspecified)

        else -> flowOf(FileSize.Unspecified)
    }
}

private fun Long.toFileSizeOrUnspecified(): FileSize = if (this < 0) FileSize.Unspecified else this.bytes
