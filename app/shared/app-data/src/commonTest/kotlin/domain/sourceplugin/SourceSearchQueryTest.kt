package com.wynime.app.domain.sourceplugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceSearchQueryTest {
    @Test
    fun `traditional source query sends only simplified outbound query`() {
        assertEquals(
            listOf(
                "遭到流放的转生重骑士凭借游戏知识大开无双",
            ),
            sourceSearchQueryVariants("遭到流放的轉生重騎士憑藉遊戲知識大開無雙"),
        )
    }

    @Test
    fun `seasonal query prioritizes base and includes equivalent season forms`() {
        assertEquals(
            listOf(
                "大王饶命",
                "大王饶命第三季",
                "大王饶命第3季",
                "大王饶命3",
            ),
            sourceSearchQueryVariants("大王饒命第三季"),
        )
    }

    @Test
    fun `Arabic and Chinese season forms have the same canonical match`() {
        val chinese = sourceTitleMatch("大王饒命第三季")
        val arabic = sourceTitleMatch("大王饶命3")

        assertEquals(chinese.canonical, arabic.canonical)
        assertEquals(3, chinese.variant?.number)
        assertTrue(chinese.hasVariantMarker)
    }
}
