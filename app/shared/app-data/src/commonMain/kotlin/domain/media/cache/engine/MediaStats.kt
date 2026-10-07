package com.wynime.app.domain.media.cache.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.wynime.app.domain.media.cache.storage.MediaCacheStorage
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes

data class MediaStats(

    val uploaded: FileSize,

    val downloaded: FileSize,

    val uploadSpeed: FileSize,

    val downloadSpeed: FileSize,
) {
    companion object {
        val Zero =
            MediaStats(FileSize.Zero, FileSize.Zero, FileSize.Zero, FileSize.Zero)
        val Unspecified =
            MediaStats(FileSize.Unspecified, FileSize.Unspecified, FileSize.Unspecified, FileSize.Unspecified)
    }
}

fun Iterable<Flow<MediaStats>>.sum(): Flow<MediaStats> = combine(this) { array -> array.sum() }

fun Array<MediaStats>.sum() = MediaStats(
    uploaded = sumOf { it.uploaded.inBytes }.bytes,
    downloaded = sumOf { it.downloaded.inBytes }.bytes,
    uploadSpeed = sumOf { it.uploadSpeed.inBytes }.bytes,
    downloadSpeed = sumOf { it.downloadSpeed.inBytes }.bytes,
)
