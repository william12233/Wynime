@file:Suppress("DEPRECATION")

package com.wynime.app.domain.media.selector.legacy

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.SubtitleLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

@Deprecated(MediaSelectorDeprecationMessage)
class DefaultMediaSelectorSubjectNameMismatchTest : AbstractDefaultMediaSelectorTest() {
    @Test
    fun `exclude name mismatch when subjectName is available`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "条目名称",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(

                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "random value",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(

                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "random value 2",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, MediaExclusionReason.SubjectNameMismatch),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `exclude name mismatch when subjectName is not available`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "条目名称",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(

                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = null,
                originalTitle = "条目名称I 第1话",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(

                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = null,
                originalTitle = "ab 第2话",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, MediaExclusionReason.SubjectNameMismatch),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `dont exclude if epInfo is null`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "条目名称",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty,
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(

                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "条目名称I",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(

                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "a",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, null),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `dont exclude if subjectInfo name is blank`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty.copy(
                nameCn = "",
            ),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(

                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "条目名称I",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(

                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "a",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, null),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `dont exclude if both infos are empty`() = runTest {
        val target: DefaultMedia
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Companion.Empty,
            episodeInfo = EpisodeInfo.Companion.Empty,
        )
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default
        savedUserPreference.value = DEFAULT_PREFERENCE
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        addMedia(
            media(

                alliance = "字幕组1",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "条目名称I",
                originalTitle = "条目名称I",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
            ).also { target = it },
            media(

                alliance = "字幕组2",
                episodeRange = EpisodeRange.Companion.single("1"), kind = MediaSourceKind.WEB,
                subjectName = "a",
                originalTitle = "a",
                subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
            ),
        )
        assertEquals(2, selector.preferredCandidates.first().size)
        assertEquals(
            listOf(null, null),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
        assertEquals(target, selector.trySelectDefault())
    }
}
