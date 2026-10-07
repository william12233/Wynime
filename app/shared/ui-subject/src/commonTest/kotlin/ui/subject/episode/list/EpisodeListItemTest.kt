package com.wynime.app.ui.subject.episode.list

import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class EpisodeListItemTest {
    private fun item(name: String, nameCn: String) =
        createTestEpisodeListItem(name = name, nameCn = nameCn)

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_returns_original_name`() {
        val episode = item(name = "転がる岩、君に朝が降る", nameCn = "滚石与朝阳")
        assertEquals("転がる岩、君に朝が降る", episode.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleFalse_returns_nameCn`() {
        val episode = item(name = "転がる岩、君に朝が降る", nameCn = "滚石与朝阳")
        assertEquals("滚石与朝阳", episode.preferredDisplayName(useOriginalTitle = false))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleTrue_falls_back_to_nameCn_when_name_is_blank`() {
        val episode = item(name = "", nameCn = "滚石与朝阳")
        assertEquals("滚石与朝阳", episode.preferredDisplayName(useOriginalTitle = true))
    }

    @Test
    fun `preferredDisplayName_with_useOriginalTitleFalse_falls_back_to_name_when_nameCn_is_blank`() {
        val episode = item(name = "転がる岩、君に朝が降る", nameCn = "")
        assertEquals("転がる岩、君に朝が降る", episode.preferredDisplayName(useOriginalTitle = false))
    }
}
