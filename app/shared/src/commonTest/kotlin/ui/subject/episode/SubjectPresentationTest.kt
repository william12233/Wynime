package com.wynime.app.ui.subject.episode

import com.wynime.app.data.models.subject.SubjectInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectPresentationTest {
    @Test
    fun `toPresentation_sets_title_and_originalTitle_from_displayName_and_name`() {
        val info = SubjectInfo.Empty.copy(name = "ぼっち・ざ・ろっく！", nameCn = "孤独摇滚！")
        val presentation = info.toPresentation()
        assertEquals("孤独摇滚！", presentation.title)
        assertEquals("ぼっち・ざ・ろっく！", presentation.originalTitle)
    }

    @Test
    fun `originalTitle_falls_back_to_title_when_name_is_blank`() {
        val info = SubjectInfo.Empty.copy(name = "", nameCn = "孤独摇滚！")
        val presentation = info.toPresentation()
        assertEquals("孤独摇滚！", presentation.title)
        assertEquals("孤独摇滚！", presentation.originalTitle)
    }
}
