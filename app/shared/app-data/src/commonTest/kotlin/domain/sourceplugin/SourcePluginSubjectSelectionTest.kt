/*
 * Copyright (C) 2026 OpenAni contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.domain.sourceplugin

import me.him188.ani.source.plugin.api.SourceSubject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
