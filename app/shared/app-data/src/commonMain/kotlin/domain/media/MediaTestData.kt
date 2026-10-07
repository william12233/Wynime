package com.wynime.app.domain.media

import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.MediaExtraFiles
import com.wynime.datasources.api.MediaProperties
import com.wynime.datasources.api.Subtitle
import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.SubtitleLanguage.ChineseSimplified
import com.wynime.datasources.api.topic.SubtitleLanguage.ChineseTraditional
import com.wynime.utils.platform.annotations.TestOnly

@TestOnly
const val SOURCE_WEB_PRIMARY = "web-primary"

@TestOnly
const val SOURCE_WEB_SECONDARY = "web-secondary"

@TestOnly
fun createTestDefaultMedia(
    mediaId: String,
    mediaSourceId: String,
    originalUrl: String,
    download: ResourceLocation,
    originalTitle: String,
    publishedTime: Long,
    properties: MediaProperties,
    episodeRange: EpisodeRange?,
    extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
    location: MediaSourceLocation,
    kind: MediaSourceKind,

): DefaultMedia = DefaultMedia(
    mediaId = mediaId,
    mediaSourceId = mediaSourceId,

    originalUrl = originalUrl,
    download = download,
    originalTitle = originalTitle,
    publishedTime = publishedTime,
    properties = properties,
    episodeRange = episodeRange,
    extraFiles = extraFiles,
    location = location,
    kind = kind,
)

fun createTestMediaProperties(
    subjectName: String? = null,
    episodeName: String? = null,
    subtitleLanguageIds: List<String> = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
    resolution: String = "1080P",
    alliance: String = "桜都字幕组",
    size: FileSize = 122.megaBytes,
    subtitleKind: SubtitleKind? = SubtitleKind.CLOSED,
): MediaProperties = MediaProperties(
    subjectName = subjectName,
    episodeName = episodeName,
    subtitleLanguageIds = subtitleLanguageIds,
    resolution = resolution,
    alliance = alliance,
    size = size,
    subtitleKind = subtitleKind,
)

@TestOnly
val TestMediaList = listOf(
    createTestDefaultMedia(
        mediaId = "$SOURCE_WEB_PRIMARY.1",
        mediaSourceId = SOURCE_WEB_PRIMARY,
        originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.HttpStreamingFile("https://example.com/1.m3u8"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
            resolution = "1080P",
            alliance = "桜都字幕组",
            size = 122.megaBytes,
            subtitleKind = SubtitleKind.CLOSED,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
        extraFiles = MediaExtraFiles(
            listOf(
                Subtitle(
                    uri = "https://example.com/1",
                    mimeType = "text/x-ass",
                    language = "简体中文",
                ),
            ),
        ),
    ),

    createTestDefaultMedia(
        mediaId = "$SOURCE_WEB_SECONDARY.1",
        mediaSourceId = SOURCE_WEB_SECONDARY,
        originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.HttpStreamingFile("https://example.com/1.m3u8"),
        originalUrl = "https://example.com/1",
        publishedTime = 2,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
            resolution = "1080P",
            alliance = "桜都字幕组",
            size = 122.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
    ),

    createTestDefaultMedia(
        mediaId = "$SOURCE_WEB_PRIMARY.2",
        mediaSourceId = SOURCE_WEB_PRIMARY,
        originalTitle = "夜晚的水母不会游泳",
        download = ResourceLocation.HttpStreamingFile("https://example.com/2.m3u8"),
        originalUrl = "https://example.com/1",
        publishedTime = 3,
        episodeRange = EpisodeRange.single(EpisodeSort(2)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseTraditional).map { it.id },
            resolution = "1080P",
            alliance = "北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组",
            size = 233.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
    ),
    createTestDefaultMedia(
        mediaId = "$SOURCE_WEB_SECONDARY.2",
        mediaSourceId = SOURCE_WEB_SECONDARY,
        originalTitle = "葬送的芙莉莲",
        download = ResourceLocation.HttpStreamingFile("https://example.com/2.m3u8"),
        originalUrl = "https://example.com/1",
        publishedTime = 4,
        episodeRange = EpisodeRange.single(EpisodeSort(2)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseSimplified).map { it.id },
            resolution = "1080P",
            alliance = "桜都字幕组",
            size = 0.bytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
    ),
    createTestDefaultMedia(
        mediaId = "$SOURCE_WEB_SECONDARY.3",
        mediaSourceId = SOURCE_WEB_SECONDARY,
        originalTitle = "某个生肉",
        download = ResourceLocation.HttpStreamingFile("https://example.com/3.m3u8"),
        originalUrl = "https://example.com/1",
        publishedTime = 5,
        episodeRange = EpisodeRange.single(EpisodeSort(3)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(),
            resolution = "1080P",
            alliance = "Lilith-Raws",
            size = 702.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.WEB,
        location = MediaSourceLocation.Online,
    ),
)

@TestOnly
val TestMediaSourceInfo = MediaSourceInfo("Web")
