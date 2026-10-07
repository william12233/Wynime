package com.wynime.datasources.api

import com.wynime.datasources.api.topic.EpisodeRange.Companion.range
import com.wynime.datasources.api.topic.titles.ParsedTopicTitle
import com.wynime.datasources.api.topic.titles.PatternBasedRawTitleParser
import com.wynime.datasources.api.topic.titles.parse
import com.wynime.test.DynamicTestsResult
import com.wynime.test.TestContainer
import com.wynime.test.TestFactory
import com.wynime.test.dynamicTest
import com.wynime.test.runDynamicTests
import kotlin.test.assertEquals

@TestContainer
internal class RawPatternBasedTitleParserTestSuite {
    private val dataA = """
            Lilith-Raws [Lilith-Raws] Overlord IV - 05 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]
            NC-Raws [NC-Raws] OVERLORD IV / Overlord S4 - 05 (B-Global 3840x2160 HEVC AAC MKV)
            桜都字幕组 [桜都字幕组] RWBY 冰雪帝国 / RWBY Hyousetsu Teikoku [05][1080p][简繁内封]
            天月動漫&發佈組 [Skymoon-Raws] 新網球王子: U-17 WORLD CUP / Shin Tennis no Ouji-sama: U-17 World Cup - 05 [ViuTV][WEB-RIP][720p][HEVC AAC][CHT][MP4]
            天月動漫&發佈組 [天月搬運組] 異世界迷宮裡的後宮生活 / Isekai Meikyuu de Harem wo - 05 [1080P][簡繁日外掛]
            LoliHouse [喵萌奶茶屋&LoliHouse] 风都侦探 / Fuuto Tantei / FUUTO PI - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]
            LoliHouse [LoliHouse] 邪神与厨二病少女X / Jashin-chan Dropkick X - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]
            LoliHouse [喵萌Production&LoliHouse] LoveLive! 超级明星!! 2期 / Love Live! Superstar!! S2 - 03 [WebRip 1080p HEVC-10bit AAC][简繁日内封字幕]
    """.trimIndent().lines()

    @TestFactory
    fun testParserA(): DynamicTestsResult {
        val parser = PatternBasedRawTitleParser()
        return runDynamicTests(
            dataA.map { data ->
                val allianceName = data.substringBefore(' ').trim()
                dynamicTest(allianceName) {
                    val builder = ParsedTopicTitle.Builder()
                    parser.parse(
                        data.substringAfter(' ').trim(),
                        allianceName,
                        builder,
                    )
                    builder.build().run {

                        println(this)

                    }
                }
            },
        )
    }

    private val episodeRangeData = listOf(
        range(1, 12)
                to "[悠哈璃羽字幕社&LoliHouse] Overtake! [01-12 合集][WebRip 1080p HEVC-10bit AAC][简繁内封字幕][Fin]",
        range(1, 77) to
                "[DBD-Raws][偶像活动！ 第三季&第四季/Aikatsu! Akari Generation/アイカツ! あかり Generation][01-77TV全集+特典映像][1080P][BDRip][HEVC-10bit][简繁外挂][FLAC][MKV]",
        range(1, 12) to
                "【喵萌奶茶屋】★01月新番★[最弱驯魔师开始了捡垃圾之旅。 / Saijaku Tamer wa Gomi Hiroi no Tabi wo Hajimemashita][01-12END][1080p][简日双语][招募翻译]",
        range(1, 12) to
                " [百冬练习组&LoliHouse] 为了在异世界也能抚摸毛茸茸而努力。 / Isekai de Mofumofu Nadenade suru Tame ni Ganbattemasu [01-12 合集][WebRip 1080p HEVC-10bit AAC][简繁内封字幕][Fin]",
    )

    @TestFactory
    fun testParseRange(): DynamicTestsResult {
        val parser = PatternBasedRawTitleParser()
        return runDynamicTests(
            buildList {
                for ((expected, title) in episodeRangeData) {
                    add(
                        dynamicTest("$expected - $title") {
                            assertEquals(
                                expected, parser.parse(title, null).episodeRange,
                            )
                        },
                    )
                }
            },
        )
    }
}
