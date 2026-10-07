package com.wynime.app.data.models.subject

import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectInfoTest {
    private fun subject(name: String, nameCn: String) =
        SubjectInfo.Empty.copy(name = name, nameCn = nameCn)

    @Test
    fun `nameOrNameCn_prefers_original_name`() {
        assertEquals("ぼっち・ざ・ろっく！", subject(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！").nameOrNameCn)
    }

    @Test
    fun `nameOrNameCn_falls_back_to_nameCn_when_name_is_blank`() {
        assertEquals("孤独摇滚！", subject(name = "", nameCn = "孤独摇滚！").nameOrNameCn)
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_returns_nameOrNameCn`() {
        val info = subject(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！")
        assertEquals(info.nameOrNameCn, info.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleFalse_returns_displayName`() {
        val info = subject(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！")
        assertEquals(info.displayName, info.preferredDisplayName(useOriginalTitle = false))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_falls_back_to_nameCn_when_name_is_blank`() {
        val info = subject(name = "", nameCn = "孤独摇滚！")
        assertEquals("孤独摇滚！", info.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `listCoverUrl_prefers_imageLarge_over_imageThumb`() {
        val info = SubjectInfo.Empty.copy(
            imageLarge = "https://example.test/large.jpg",
            imageThumb = "https://example.test/thumb.jpg",
        )

        assertEquals("https://example.test/large.jpg", info.listCoverUrl)
    }

    @Test
    fun `listCoverUrl_falls_back_to_imageThumb_when_imageLarge_is_blank`() {
        val info = SubjectInfo.Empty.copy(
            imageLarge = "",
            imageThumb = "https://example.test/thumb.jpg",
        )

        assertEquals("https://example.test/thumb.jpg", info.listCoverUrl)
    }
}
