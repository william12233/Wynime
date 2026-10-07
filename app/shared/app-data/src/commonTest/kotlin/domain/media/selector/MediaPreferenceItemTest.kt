package com.wynime.app.domain.media.selector

import kotlinx.coroutines.flow.first
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_PRIMARY_WEB
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_SECONDARY_WEB
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestContainer
class MediaPreferenceItemTest {
    @Test
    fun `ITEM-02 会话 override 屏蔽数据库后续更新`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "字幕组A")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty

        assertEquals("字幕组A", selector.alliance.finalSelected.first())

        selector.alliance.prefer("X")
        assertEquals("X", selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Y")
        assertEquals("X", selector.alliance.finalSelected.first())
    }

    @Test
    fun `ITEM-02 removePreference 后数据库更新与全局默认值同时被屏蔽`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "字幕组A")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(alliance = "默认组")

        selector.alliance.removePreference()
        assertNull(selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Y")
        assertEquals("默认组", selector.alliance.defaultSelected.first())
        assertNull(selector.alliance.finalSelected.first())
    }

    @Test
    fun `ITEM-02 无 override 时数据库更新跟随生效`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(alliance = "默认组")

        assertEquals("默认组", selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Y")
        assertEquals("Y", selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Z")
        assertEquals("Z", selector.alliance.finalSelected.first())
    }

    @Test
    fun `ITEM-03 alliance 与 resolution 的 available 去重后按字典序升序`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(alliance = "b组", resolution = "720P"),
            media(alliance = "A组", resolution = "1080P"),
            media(alliance = "B组", resolution = "2160P"),
            media(alliance = "A组", resolution = "1080P"),
        )

        assertEquals(listOf("A组", "B组", "b组"), selector.alliance.available.first())
        assertEquals(listOf("1080P", "2160P", "720P"), selector.resolution.available.first())
    }

    @Test
    fun `ITEM-04 subtitleLanguageId 的 available 排序表无效仅集合稳定`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(subtitleLanguages = listOf("CHS", "JPN")),
            media(subtitleLanguages = listOf("CHT", "CHS")),
            media(subtitleLanguages = listOf("ENG")),
        )

        val available = selector.subtitleLanguageId.available.first()

        assertEquals(4, available.size)
        assertEquals(setOf("CHS", "CHT", "JPN", "ENG"), available.toSet())
    }

    @Test
    fun `ITEM-04 排序权重表命中的是分辨率字样`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(subtitleLanguages = listOf("CHS")),
            media(subtitleLanguages = listOf("720P")),
        )

        assertEquals(listOf("720P", "CHS"), selector.subtitleLanguageId.available.first())
    }

    @Test
    fun `ITEM-05 mediaSourceId 的 available 误取分辨率集合`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(sourceId = SOURCE_PRIMARY_WEB, resolution = "1080P"),
            media(sourceId = SOURCE_SECONDARY_WEB, resolution = "720P"),
            media(sourceId = SOURCE_SECONDARY_WEB, resolution = "1080P"),
        )

        assertEquals(listOf("1080P", "720P"), selector.mediaSourceId.available.first())
    }
}
