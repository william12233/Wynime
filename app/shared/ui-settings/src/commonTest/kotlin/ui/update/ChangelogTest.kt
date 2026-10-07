package com.wynime.app.ui.update

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangelogTest {
    @Test
    fun `removes Full Changelog`() {
        assertEquals(
            """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集
            """.trimIndent(),
            Changelog(
                "", "",
                """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集

                **Full Changelog**: https://github.com/william12233/Wynime/compare/v4.0.0-beta04...v4.0.0-beta05
                """.trimIndent(),
            ).changes,
        )
    }

    @Test
    fun `removes Full Changelog no match`() {
        assertEquals(
            """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集
            """.trimIndent(),
            Changelog(
                "", "",
                """
                - 修复一些弱网环境下的细节问题
                - 修复启动时可能的崩溃
                - 修复识别电影剧集
                """.trimIndent(),
            ).changes,
        )
    }
}
