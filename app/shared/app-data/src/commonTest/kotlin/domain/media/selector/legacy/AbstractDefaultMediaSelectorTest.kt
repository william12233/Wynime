@file:Suppress("DEPRECATION")

package com.wynime.app.domain.media.selector.legacy

import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.domain.media.createTestDefaultMedia
import com.wynime.app.domain.media.createTestMediaProperties
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.media.selector.MediaSelectorSubtitlePreferences
import com.wynime.app.domain.media.selector.SubtitleKindPreference
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.MediaExtraFiles
import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.Resolution
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.SubtitleLanguage
import com.wynime.utils.platform.collections.copyPut
import com.wynime.utils.platform.collections.toImmutable

@Deprecated(MediaSelectorDeprecationMessage)
abstract class AbstractDefaultMediaSelectorTest {
    protected val mediaList: MutableStateFlow<MutableList<DefaultMedia>> = MutableStateFlow(mutableListOf())
    protected fun addMedia(vararg media: DefaultMedia) {
        mediaList.value.addAll(media)
    }

    protected val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    protected val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
    protected val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Companion.Default)
    protected val mediaSelectorContext = MutableStateFlow(
        createMediaSelectorContextFromEmpty(),
    )

    protected fun setSubtitlePreferences(
        preferences: MediaSelectorSubtitlePreferences = getCurrentSubtitlePreferences()
    ) {
        mediaSelectorContext.value = mediaSelectorContext.value.run {
            copy(subtitlePreferences = preferences)
        }
    }

    private fun getCurrentSubtitlePreferences() = (mediaSelectorContext.value.subtitlePreferences
        ?: MediaSelectorSubtitlePreferences.Companion.AllNormal)

    protected fun setSubtitlePreference(
        key: SubtitleKind,
        value: SubtitleKindPreference
    ) {
        setSubtitlePreferences(
            MediaSelectorSubtitlePreferences(getCurrentSubtitlePreferences().values.copyPut(key, value).toImmutable()),
        )
    }

    protected val selector = DefaultMediaSelector(
        mediaSelectorContextNotCached = mediaSelectorContext,
        mediaListNotCached = mediaList,
        savedUserPreference = savedUserPreference,
        savedDefaultPreference = savedDefaultPreference,
        enableCaching = false,
        mediaSelectorSettings = mediaSelectorSettings,
    )

    companion object {
        val DEFAULT_PREFERENCE = MediaPreference.Companion.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.Companion.R2160P,
                Resolution.Companion.R1440P,
                Resolution.Companion.R1080P,
                Resolution.Companion.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                SubtitleLanguage.ChineseSimplified,
                SubtitleLanguage.ChineseTraditional,
            ).map { it.id },
        )

        const val SOURCE_PRIMARY_WEB = "web-primary"
        const val SOURCE_SECONDARY_WEB = "web-secondary"

        @Suppress("SameParameterValue", "INVISIBLE_REFERENCE")
        @kotlin.internal.LowPriorityInOverloadResolution
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.Companion.AllNormal,
            subjectSequelNames: Set<String> = emptySet(),
            subjectInfo: SubjectInfo = SubjectInfo.Companion.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Companion.Empty,
        ) = createMediaSelectorContextFromEmpty(
            subjectCompleted = subjectCompleted,
            mediaSourcePrecedence = mediaSourcePrecedence,
            subtitleKindFilters = subtitleKindFilters,
            subjectSeriesInfo = SubjectSeriesInfo.Companion.Fallback.copy(sequelSubjectNames = subjectSequelNames),
            subjectInfo = subjectInfo,
            episodeInfo = episodeInfo,
        )

        @Suppress("SameParameterValue")
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.Companion.AllNormal,
            subjectSeriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Companion.Fallback,
            subjectInfo: SubjectInfo = SubjectInfo.Companion.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Companion.Empty,
        ) =
            MediaSelectorContext(
                subjectFinished = subjectCompleted,
                mediaSourcePrecedence = mediaSourcePrecedence,
                subtitlePreferences = subtitleKindFilters,
                subjectSeriesInfo = subjectSeriesInfo,
                subjectInfo = subjectInfo,
                episodeInfo = episodeInfo,
                mediaSourceTiers = MediaSelectorSourceTiers.Companion.Empty,
            )
    }

    private var mediaIdCounter: Int = 0
    fun media(
        sourceId: String = SOURCE_PRIMARY_WEB,
        resolution: String = "1080P",
        alliance: String = "字幕组",
        size: FileSize = 1.megaBytes,
        publishedTime: Long = 0,
        subtitleLanguages: List<String> = listOf(
            SubtitleLanguage.ChineseSimplified,
            SubtitleLanguage.ChineseTraditional,
        ).map { it.id },
        location: MediaSourceLocation = MediaSourceLocation.Online,
        kind: MediaSourceKind = MediaSourceKind.WEB,
        episodeRange: EpisodeRange = EpisodeRange.Companion.single(EpisodeSort(1)),
        subtitleKind: SubtitleKind? = null,
        extraFiles: MediaExtraFiles = MediaExtraFiles.Companion.EMPTY,
        id: Int = mediaIdCounter++,
        originalTitle: String = "[字幕组] 孤独摇滚 $id",
        subjectName: String? = null,
        episodeName: String? = null,
        mediaId: String = "$sourceId.$id",
    ): DefaultMedia {
        return createTestDefaultMedia(
            mediaId = mediaId,
            mediaSourceId = sourceId,
            originalTitle = originalTitle,
            download = ResourceLocation.HttpStreamingFile("https://example.com/$id.m3u8"),
            originalUrl = "https://example.com/$id",
            publishedTime = publishedTime,
            episodeRange = episodeRange,
            properties = createTestMediaProperties(
                subjectName = subjectName,
                episodeName = episodeName,
                subtitleLanguageIds = subtitleLanguages,
                resolution = resolution,
                alliance = alliance,
                size = size,
                subtitleKind = subtitleKind,
            ),
            location = location,
            kind = kind,
            extraFiles = extraFiles,
        )
    }
}
