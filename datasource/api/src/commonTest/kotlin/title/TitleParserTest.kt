@file:Suppress("TestFunctionName")

package com.wynime.datasources.api.title

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleParserTest : PatternBasedTitleParserTestSuite() {
    @Test
    fun `Baha as CHT`() {
        val r = parse("""[Up to 21℃] 怪人的沙拉碗 / Henjin no Salad Bowl - 10 (Baha 1920x1080 AVC AAC MP4)""")
        assertEquals("10..10", r.episodeRange.toString())
        assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }

    @Test
    fun S01() {
        val r = parse("""[Up to 21℃] 怪人的沙拉碗 / Henjin no Salad Bowl - S01 (Baha 1920x1080 AVC AAC MP4)""")
        assertEquals("S1", r.episodeRange.toString())
        assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }

    @Test
    fun S1() {
        val r = parse("""[Up to 21℃] 怪人的沙拉碗 / Henjin no Salad Bowl - S1 (Baha 1920x1080 AVC AAC MP4)""")
        assertEquals("S1", r.episodeRange.toString())
        assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }

    @Test
    fun `S01E05 as ep 05`() {
        val r = parse("""[Up to 21℃] 怪人的沙拉碗 / Henjin no Salad Bowl - S01E05 (Baha 1920x1080 AVC AAC MP4)""")
        assertEquals("05..05", r.episodeRange.toString())
        assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }

    @Test
    fun special() {
        val r = parse("特典映像/[DBD-Raws] [龙猫] [特典映像] [01][1080P][BDRip][HEVC-10bit][AC3].mkv")
        assertEquals("SP01..SP01", r.episodeRange.toString())
        assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }

    @Test
    fun `movie v2 as 01`() {
        val r = parse("[北宇治字幕组] 蓦然回首 / Look Back [Movie v2][WebRip][HEVC_AAC×2][简繁日内封]")
        assertEquals("S?", r.episodeRange.toString())
        assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("null", r.resolution.toString())
    }

    @Test
    fun `movie as 01`() {
        val r = parse("[北宇治字幕组] 蓦然回首 / Look Back [Movie][WebRip][HEVC_AAC×2][简繁日内封]")
        assertEquals("S?", r.episodeRange.toString())
        assertEquals("CHS, CHT, JPN", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("null", r.resolution.toString())
    }

    @Test
    fun `no subtitle`() {
        val r = parse("[北宇治字幕组] 蓦然回首 / Look Back [Movie][WebRip][HEVC_AAC×2][无中文字幕]")
        assertEquals("S?", r.episodeRange.toString())
        assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("null", r.resolution.toString())
    }

    @Test
    fun `no subtitle even if matches`() {
        val r = parse("[北宇治字幕组] 蓦然回首 / Look Back [Movie][WebRip][HEVC_AAC×2][简繁日内封][无中文字幕]")
        assertEquals("S?", r.episodeRange.toString())
        assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("null", r.resolution.toString())
    }

    @Test
    fun `S1-S2`() {
        val r = parse("[H-Enc] 我心里危险的东西 / Boku no Kokoro no Yabai Yatsu S1-S2 (BDRip 1080p HEVC AAC)\n")
        assertEquals("S1+S2", r.episodeRange.toString())
        assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }

    @Test
    fun `S1-S3`() {
        val r = parse("[H-Enc] 我心里危险的东西 / Boku no Kokoro no Yabai Yatsu S1-S3 (BDRip 1080p HEVC AAC)\n")
        assertEquals("S1+S2+S3", r.episodeRange.toString())
        assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }

    @Test
    fun `S01E01-`() {
        val r = parse("Mahou Shoujo ni Akogarete S01E01-[1080p][BDRIP][x265.FLAC].mkv")
        assertEquals("01..01", r.episodeRange.toString())
        assertEquals("", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
        assertEquals("1080P", r.resolution.toString())
    }
}