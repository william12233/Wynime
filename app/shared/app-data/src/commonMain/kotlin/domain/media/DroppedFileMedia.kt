package com.wynime.app.domain.media

import kotlinx.io.files.Path
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaProperties
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.extension
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.length
import com.wynime.utils.io.name

object DroppedFileMedia {

    const val MEDIA_SOURCE_ID = "dropped-file"

    val VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "wmv",
        "ts", "m2ts", "mpg", "mpeg", "rm", "rmvb", "3gp", "ogv",
    )

    private val MPEG_TS_EXTENSIONS = setOf("ts", "m2ts")

    fun isVideoFile(path: Path): Boolean = path.extension.lowercase() in VIDEO_EXTENSIONS

    fun findVideoFile(files: List<Path>): SystemPath? = files.firstOrNull { isVideoFile(it) }?.inSystem

    fun isDroppedFile(media: Media): Boolean = media.mediaSourceId == MEDIA_SOURCE_ID

    fun create(file: SystemPath): Media {
        val absolutePath = file.absolutePath
        return DefaultMedia(
            mediaId = "$MEDIA_SOURCE_ID:$absolutePath",
            mediaSourceId = MEDIA_SOURCE_ID,
            originalUrl = "file://$absolutePath",
            download = ResourceLocation.LocalFile(
                absolutePath,
                fileType = ResourceLocation.LocalFile.FileType.MPTS
                    .takeIf { file.extension.lowercase() in MPEG_TS_EXTENSIONS },
            ),
            originalTitle = file.name,
            publishedTime = 0,
            properties = MediaProperties(
                subjectName = null,
                episodeName = null,
                subtitleLanguageIds = emptyList(),
                resolution = "",
                alliance = "",
                size = runCatching { file.length() }.getOrNull()?.takeIf { it > 0 }?.bytes ?: FileSize.Unspecified,
                subtitleKind = null,
            ),
            episodeRange = null,
            location = MediaSourceLocation.Local,

            kind = MediaSourceKind.WEB,
        )
    }
}
