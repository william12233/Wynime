package com.wynime.app.domain.sourceplugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceSearchQueryTest {
    @Test
    fun `traditional source query keeps original and simplified outbound queries`() {
        assertEquals(
            listOf(
                "遭到流放的轉生重騎士憑藉遊戲知識大開無雙",
                "遭到流放的转生重骑士凭借游戏知识大开无双",
            ),
            sourceSearchQueryVariants("遭到流放的轉生重騎士憑藉遊戲知識大開無雙"),
        )
    }

    @Test
    fun `seasonal query prioritizes base and includes equivalent season forms`() {
        assertEquals(
            listOf(
                "大王饒命",
                "大王饶命",
                "大王饒命第三季",
                "大王饶命第三季",
                "大王饒命第3季",
                "大王饶命第3季",
                "大王饒命3",
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

    @Test
    fun `season aliases include compact Arabic season spelling`() {
        assertTrue(sourceTitleSeasonVariants("大王饒命第三季").contains("大王饶命3"))
    }

    @Test
    fun `arc title fallback keeps the base query and specific marker`() {
        val target = sourceSearchQueryVariantsForRequest(
            queryNames = listOf("新网球王子 U-17 世界杯 半决赛"),
        )
        assertTrue(target.contains("新网球王子"))
        assertTrue(target.contains("新网球王子 u-17 世界杯 半决赛"))
        assertEquals(
            sourceTitleMatch("新网球王子 U-17 世界杯 半决赛").canonical,
            sourceTitleMatch("新网球王子 U-17 世界杯 SEMIFINAL").canonical,
        )

        val unrelated = sourceSearchQueryVariantsForRequest(
            queryNames = listOf("新网球王子 U-17 世界杯"),
        )
        assertFalse(unrelated.contains("半决赛"))
    }

    @Test
    fun `final season fallback is shared by other arc titles`() {
        val queries = sourceSearchQueryVariants("進撃の巨人 The Final Season Part.2")
        assertTrue(queries.contains("進撃の巨人"))
        assertEquals(
            sourceTitleMatch("進擊的巨人 最終季").canonical,
            sourceTitleMatch("進擊的巨人 The Final Season").canonical,
        )
    }
}
