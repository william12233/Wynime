package com.wynime.app.domain.media.selector

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_SECONDARY_WEB
import com.wynime.app.domain.media.selector.testFramework.collectEvents
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorDecisionCoreTest {
    @Test
    fun `TRY-08 只移除到候选非空即停`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliance = "组A",
            resolution = "1080P",
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))

        assertEquals(emptyList(), selector.preferredCandidatesMedia.first())

        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        assertTrue(selector.alliance.userSelected.first().isPreferNoValue)
        assertEquals("1080P", selector.resolution.finalSelected.first())
        assertEquals("1080P", selector.resolution.userSelected.first().preferredValueOrNull)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
        assertEquals(
            listOf(MediaPreference.Empty.copy(resolution = "1080P")),
            collected.onChangePreference.map { it.preference },
        )
        assertEquals(1, collected.records.size)
    }

    @Test
    fun `TRY-08 四项全不匹配时全部移除并恰好广播 4 次偏好事件`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliance = "组A",
            resolution = "720P",
            subtitleLanguageId = "CHT",
            mediaSourceId = SOURCE_SECONDARY_WEB,
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))

        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    resolution = "720P",
                    subtitleLanguageId = "CHT",
                    mediaSourceId = SOURCE_SECONDARY_WEB,
                ),
                MediaPreference.Empty.copy(subtitleLanguageId = "CHT", mediaSourceId = SOURCE_SECONDARY_WEB),
                MediaPreference.Empty.copy(mediaSourceId = SOURCE_SECONDARY_WEB),
                MediaPreference.Empty,
            ),
            collected.onChangePreference.map { it.preference },
        )
        assertEquals(4, collected.records.size)
        assertTrue(selector.alliance.userSelected.first().isPreferNoValue)
        assertTrue(selector.resolution.userSelected.first().isPreferNoValue)
        assertTrue(selector.subtitleLanguageId.userSelected.first().isPreferNoValue)
        assertTrue(selector.mediaSourceId.userSelected.first().isPreferNoValue)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `TRY-08 候选已非空时不移除任何偏好也不广播事件`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "组B")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        mediaApi.addMedia(media(alliance = "组B", subtitleLanguages = listOf("CHS")))

        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        collected.expectNoEvents()
        assertEquals("组B", selector.alliance.userSelected.first().preferredValueOrNull)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `TRY-08 caching 开启时移除后复查走未缓存流仍只移除 alliance`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
        buildTest = {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
                alliance = "组A",
                resolution = "1080P",
            )
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
            mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        },
    ) {
        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        assertEquals(1, collected.onChangePreference.size)
        assertEquals(1, collected.records.size)

        assertEquals(
            listOf(MediaPreference.Empty.copy(resolution = "1080P")),
            collected.onChangePreference.map { it.preference },
        )
        assertTrue(selector.alliance.userSelected.first().isPreferNoValue)
        assertEquals("1080P", selector.resolution.finalSelected.first())

        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `FIND-04 不为 4K 换语言`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            fallbackResolutions = listOf("2160P", "1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )
        mediaApi.addMedia(media(alliance = "组A", resolution = "2160P", subtitleLanguages = listOf("JPN")))
        val chs1080P = mediaApi.addMedia(
            media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(chs1080P, selector.trySelectDefault())
        assertEquals(chs1080P, selector.selected.value)
    }

    @Test
    fun `FIND-04 对照组 2160P 有想要语言时分辨率优先`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            fallbackResolutions = listOf("2160P", "1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )

        mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        val chs2160P = mediaApi.addMedia(
            media(alliance = "组A", resolution = "2160P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(chs2160P, selector.trySelectDefault())
        assertEquals(chs2160P, selector.selected.value)
    }

    @Test
    fun `FIND-04 caching 开启时不为 4K 换语言`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
        buildTest = {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
                fallbackResolutions = listOf("2160P", "1080P"),
                fallbackSubtitleLanguageIds = listOf("CHS"),
            )
            mediaApi.addMedia(media(alliance = "组A", resolution = "2160P", subtitleLanguages = listOf("JPN")))
            mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        },
    ) {

        val chs1080P = mediaApi.mediaList.value[1]

        assertEquals(chs1080P, selector.trySelectDefault())
        assertEquals(chs1080P, selector.selected.value)
    }

    @Test
    fun `FIND-05 偏好字幕组未命中时不降级语言`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("桜都"),
            fallbackResolutions = listOf("1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS", "CHT"),
        )
        val chsOtherAlliance = mediaApi.addMedia(
            media(alliance = "LoliHouse", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )
        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHT")))

        assertEquals(chsOtherAlliance, selector.trySelectDefault())
    }

    @Test
    fun `FIND-05 对照组 同语言存在偏好字幕组时选它`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("桜都"),
            fallbackResolutions = listOf("1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS", "CHT"),
        )
        mediaApi.addMedia(media(alliance = "LoliHouse", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHT")))
        val chsPreferredAlliance = mediaApi.addMedia(
            media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(chsPreferredAlliance, selector.trySelectDefault())
    }

    @Test
    fun `FIND-10 alliancePatterns 匹配池为全量列表而候选子集命中为空时回落`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(resolution = "1080P")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("^桜都"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )
        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "720P", subtitleLanguages = listOf("CHS")))
        val fallback = mediaApi.addMedia(
            media(alliance = "LoliHouse", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(listOf(fallback), selector.preferredCandidatesMedia.first())
        assertTrue("桜都字幕组" in selector.alliance.available.first())

        assertEquals(fallback, selector.trySelectDefault())
        assertEquals(fallback, selector.selected.value)
    }

    @Test
    fun `FIND-10 单个 pattern 多命中时按全量池的字典序尝试而非候选列表原序`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("^桜都"),
            fallbackResolutions = listOf("1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )

        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        val sakuraDonghua = mediaApi.addMedia(
            media(alliance = "桜都动漫", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(listOf("桜都动漫", "桜都字幕组"), selector.alliance.available.first())
        assertEquals(
            listOf("桜都字幕组", "桜都动漫"),
            selector.preferredCandidatesMedia.first().map { it.properties.alliance },
        )

        assertEquals(sakuraDonghua, selector.trySelectDefault())
        assertEquals(sakuraDonghua, selector.selected.value)
    }
}
