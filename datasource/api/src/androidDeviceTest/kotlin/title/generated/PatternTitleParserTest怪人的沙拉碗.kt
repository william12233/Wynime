@file:Suppress(
  "FunctionName",
  "ClassName",
  "RedundantVisibilityModifier",
  "PackageDirectoryMismatch",
  "NonAsciiCharacters",
  "SpellCheckingInspection",
)

import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.title.PatternBasedTitleParserTestSuite
import kotlin.test.Test
import kotlin.test.assertEquals

public class PatternTitleParserTest怪人的沙拉碗 : PatternBasedTitleParserTestSuite() {
  @Test
  public fun `671495-671468-671434-671433-671061-671027-670845-670826-670825-670568`() {
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 10 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("10..10", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 09 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][09][1080p][简体][招募翻译时轴]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][09][1080p][繁體][招募翻譯時軸]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 09 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 09 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("09..09", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 08 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][08][1080p][繁體][招募翻譯時軸]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][08][1080p][简体][招募翻译时轴]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 08 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `670477-670251-670247-670246-670182-670138-669913-669798-669793-669792`() {
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 08 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("08..08", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 07 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][07][1080p][繁體][招募翻譯時軸]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][07][1080p][简体][招募翻译时轴]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 07 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 06 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 07 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("07..07", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 06 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][06][1080p][繁體][招募翻譯時軸]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][06][1080p][简体][招募翻译时轴]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `669195-668956-668955-668954-668714-668652-668632-668468-668441-668440`() {
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 06 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("06..06", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][05][1080p][繁體][招募翻譯時軸]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][05][1080p][简体][招募翻译时轴]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 05 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 05 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 04 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 05 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("05..05", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][04][1080p][繁體][招募翻譯時軸]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][04][1080p][简体][招募翻译时轴]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `668193-667889-667882-667881-667659-667591-667371-667369-667368-667045`() {
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 04 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("04..04", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 03 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][03][1080p][繁體][招募翻譯時軸]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][03][1080p][简体][招募翻译时轴]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 03 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("03..03", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 02 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][02][1080p][繁體][招募翻譯時軸]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][02][1080p][简体][招募翻译时轴]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 02 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }

  @Test
  public fun `666934-666569-666374-666373-666169-666130`() {
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("02..02", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[喵萌奶茶屋&LoliHouse] 怪人的沙拉碗 / Henjin no Salad Bowl - 01 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS, CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(SubtitleKind.CLOSED, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][01][1080p][繁體][招募翻譯時軸]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r = parse("【喵萌奶茶屋】★04月新番★[怪人的沙拉碗 / Henjin no Salad Bowl][01][1080p][简体][招募翻译时轴]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHS", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[Lilith-Raws] 怪人的沙拉碗 / Henjin no Salad Bowl - 01 [Baha][WebDL 1080p AVC AAC][CHT]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
    kotlin.run {
    val r =
        parse("[ANi] Henjin no Salad Bowl / 怪人的沙拉碗 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT][MP4]")
    assertEquals("01..01", r.episodeRange.toString())
    assertEquals("CHT", r.subtitleLanguages.sortedBy { it.id }.joinToString { it.id })
    assertEquals("1080P", r.resolution.toString())
    assertEquals(null, r.subtitleKind)
    }
  }
}

