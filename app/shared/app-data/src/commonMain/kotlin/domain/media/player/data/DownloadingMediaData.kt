package com.wynime.app.domain.media.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.openani.mediamp.source.MediaData

sealed interface DownloadingMediaData {

    val networkStats: Flow<NetStats>

    val isCacheFinished: Flow<Boolean> get() = flowOf(false)
}

sealed interface FileMediaData {
    val filename: String?
}

val MediaData.filenameOrNull: String? get() = (this as? FileMediaData)?.filename

class NetStats(

    val downloadSpeed: Long,

    val uploadRate: Long,
)

