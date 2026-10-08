package com.wynime.app.domain.sourceplugin

import com.wynime.source.plugin.api.SourceSubject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourcePluginSubjectSelectionTest {
    @Test
    fun `exact title wins over other seasons returned by the same search`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(
                SourceSubject("season-3", "关于我转生变成史莱姆这档事 第三季"),
                SourceSubject("season-1", "关于我转生变成史莱姆这档事"),
                SourceSubject("movie", "关于我转生变成史莱姆这档事 剧场版"),
            ),
            queryNames = listOf("关于我转生变成史莱姆这档事"),
        )

        assertEquals("season-1", selected?.subject?.id)
        assertEquals(true, selected?.isExactTitle)
    }

    @Test
    fun `variant-only result is not used for a base title`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(
                SourceSubject("diary", "转生史莱姆日记"),
                SourceSubject("movie", "关于我转生变成史莱姆这档事 剧场版"),
            ),
            queryNames = listOf("关于我转生变成史莱姆这档事"),
        )

        assertNull(selected)
    }

    @Test
    fun `first season marker is accepted for a base title`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(
                SourceSubject("season-4", "Re：从零开始的异世界生活 第四季 夺还篇"),
                SourceSubject("movie", "Re：从零开始的异世界生活 冰结之绊"),
                SourceSubject("season-1", "Re：从零开始的异世界生活 第一季"),
            ),
            queryNames = listOf("Re：从零开始的异世界生活"),
        )

        assertEquals("season-1", selected?.subject?.id)
    }

    @Test
    fun `cover image suffix does not change an exact base title`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(
                SourceSubject("base", "Re：从零开始的异世界生活封面图"),
                SourceSubject("movie", "Re：从零开始的异世界生活 雪之回忆"),
            ),
            queryNames = listOf("Re：从零开始的异世界生活"),
        )

        assertEquals("base", selected?.subject?.id)
        assertEquals(true, selected?.isExactTitle)
    }

    @Test
    fun `traditional query matches simplified source title`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(
                SourceSubject("next-3410", "遭到流放的转生重骑士凭借游戏知识大开无双"),
            ),
            queryNames = listOf("遭到流放的轉生重騎士憑藉遊戲知識大開無雙"),
        )

        assertEquals("next-3410", selected?.subject?.id)
        assertEquals(true, selected?.isExactTitle)
    }

    @Test
    fun `Chinese season query selects matching compact Arabic season`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(
                SourceSubject("base", "大王饶命"),
                SourceSubject("season-2", "大王饶命2"),
                SourceSubject("season-3", "大王饶命3"),
            ),
            queryNames = listOf("大王饒命第三季"),
        )

        assertEquals("season-3", selected?.subject?.id)
        assertTrue(selected?.isExactTitle == true)
    }

    @Test
    fun `base query does not select compact seasonal title`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(SourceSubject("season-3", "大王饶命3")),
            queryNames = listOf("大王饶命"),
        )

        assertNull(selected)
    }
}
