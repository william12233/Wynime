package com.wynime.app.data.network

import com.wynime.datasources.bangumi.models.BangumiItem
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiSubjectAliasesTest {
    @Test
    fun `extracts every alias value from Bangumi infobox`() {
        val infobox = listOf(
            BangumiItem(
                key = "别名",
                value = Json.parseToJsonElement(
                    """[
                        {"v":"转生成为魔剑了"},
                        {"v":"转生成为魔剑"},
                        {"v":"Reincarnated as a Sword"}
                    ]""",
                ),
            ),
            BangumiItem(
                key = "別名",
                value = Json.parseToJsonElement(
                    """[{"v":"转生成为魔剑"}, {"v":"TenKen"}]""",
                ),
            ),
            BangumiItem(
                key = "官方网站",
                value = Json.parseToJsonElement("\"https://example.com\""),
            ),
        )

        assertEquals(
            listOf("转生成为魔剑了", "转生成为魔剑", "Reincarnated as a Sword", "TenKen"),
            extractBangumiAliases(infobox),
        )
    }

    @Test
    fun `missing aliases produce an empty list`() {
        assertEquals(emptyList(), extractBangumiAliases(null))
    }
}
