package com.wynime.app.domain.media.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatform
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.SystemFileMediaData

class SystemFileMediaDataProvider internal constructor(
    val path: Path,
    override val extraFiles: MediaExtraFiles,
    private val fileType: ResourceLocation.LocalFile.FileType?,
) : MediaDataProvider<WynimeSystemFileMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): WynimeSystemFileMediaData =
        WynimeSystemFileMediaData(
            SystemFileMediaData(
                path, extraFiles,
                options = getOptions() ?: emptyList(),
            ),
        )

    private fun getOptions(): List<String>? = when (currentPlatform()) {
        is Platform.Desktop -> {
            when (fileType) {
                null -> null
                ResourceLocation.LocalFile.FileType.MPTS -> {

                    listOf(
                        ":demux=avformat",
                        ":avformat-options=probesize=524288000,analyzeduration=10000000",
                    )
                }

                ResourceLocation.LocalFile.FileType.CONTAINED -> {
                    emptyList()
                }
            }
        }

        is Platform.Android -> null
    }

    override fun toString(): String = "SystemFileMediaDataProvider(path=$path)"
}

@OptIn(ExperimentalMediampApi::class)
class WynimeSystemFileMediaData(
    val delegate: SystemFileMediaData,
) : SeekableInputMediaData by delegate, FileMediaData {
    override val filename: String get() = delegate.file.name
}
