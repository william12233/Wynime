package com.wynime.app.domain.media.download

import com.wynime.app.domain.mediasource.MediaListFilters
import com.wynime.datasources.api.Media

internal fun Media.lineSubjectNames(): Set<String> {
    properties.subjectName?.takeIf { it.isNotBlank() }?.let { return setOf(it.trim()) }
    val title = originalTitle.replace(TITLE_EXTENSION, "").replace(TITLE_DECORATIONS, " ")
    val plain = title.replace(TITLE_TAGS, " ").titleSegments()

    val leadingTagStart = title.indexOfFirst { !it.isWhitespace() }
    val tagged = TITLE_TAGS.findAll(title)
        .filter { it.range.first != leadingTagStart }
        .map { it.value.drop(1).dropLast(1) }
        .filter { !it.equals(properties.alliance, ignoreCase = true) && (it.trim().isSeasonMarker() || !it.matches(TITLE_TAG_WORDS)) }
        .flatMap { it.titleSegments() }
    return plain + tagged
}

private fun String.titleSegments(): Set<String> = split(TITLE_SEPARATORS)
    .map { it.replace(TITLE_EPISODE_TOKENS, " ").trim() }
    .filterTo(hashSetOf()) { it.contains(TITLE_WORD_CHAR) }

internal fun Media.isSameLineAs(other: Media, otherNames: Set<String>, subjectNames: Collection<String>): Boolean {
    if (mediaSourceId != other.mediaSourceId || properties.alliance != other.properties.alliance) return false
    if (mediaId == other.mediaId) return true
    val names = lineSubjectNames()
    val markers = names.seasonMarkers()
    val otherMarkers = otherNames.seasonMarkers()
    if (markers != otherMarkers) return false
    if (names.isNotEmpty() && names == otherNames) return true
    val subjects = subjectNames.filter { it.isNotBlank() }
    if (names.isEmpty() || otherNames.isEmpty()) {
        return subjects.any { MediaListFilters.specialContains(originalTitle, it) } &&
                subjects.any { MediaListFilters.specialContains(other.originalTitle, it) }
    }
    val core = names.filter { it !in markers }
    val otherCore = otherNames.filter { it !in otherMarkers }
    if (core.isNotEmpty() && otherCore.isNotEmpty() && (core.containsAll(otherCore) || otherCore.containsAll(core))) return true
    fun String.isSubjectName() = subjects.any { MediaListFilters.specialEquals(this, it) }
    fun String.mentionsSubject() = subjects.any { MediaListFilters.specialContains(this, it) }
    return names.any { name ->
        name.isSubjectName() || otherNames.any { MediaListFilters.specialEquals(name, it) && it.mentionsSubject() }
    }
}

private fun Set<String>.seasonMarkers(): Set<String> = filterTo(hashSetOf()) { it.isSeasonMarker() }

private fun String.isSeasonMarker(): Boolean = matches(TITLE_SEASON_MARKER)

private val TITLE_EXTENSION = Regex("""\.(mp4|mkv|avi|mpeg|mov|flv|wmv|webm|rm|rmvb|ts|m2ts)$""", RegexOption.IGNORE_CASE)

private val TITLE_TAGS = Regex("""\[[^\]]*\]|【[^】]*】|\([^)]*\)|（[^）]*）|\{[^}]*\}""")
private val TITLE_SEPARATORS = Regex("""\s*[/|｜／]\s*|\s+[-–—－]\s+""")

private val TITLE_DECORATIONS = Regex("""★[^★\[【(（]*★|★|☆|\d+\s*月\s*新番""")

private val TITLE_EPISODE_TOKENS = Regex(
    """第\s*\d+(?:\.\d+)?(?:\s*[-~至]\s*\d+(?:\.\d+)?)?(?!\s*[季期部])\s*[话話集]?(?:\s*v\d)?|全\s*\d+\s*[话話集]|全集|合集|完结|[(（]\s*(?:完|完结|终|終|end|fin)\s*[)）]|""" +
            """(?<![A-Za-z0-9])(?:ep|e|sp|ova|oad)\s*\d{1,4}(?:\.\d)?(?:v\d)?(?:\s*[-~至]\s*\d{1,4})?(?![A-Za-z0-9])|""" +
            """(?<![A-Za-z0-9])\d{2,4}(?:\.\d)?(?:v\d)?(?:\s*[-~至]\s*\d{1,4})?(?![A-Za-z0-9])|""" +
            """(?<![A-Za-z])(?:fin|end|complete)(?![A-Za-z])""",
    RegexOption.IGNORE_CASE,
)

private val TITLE_SEASON_MARKER = Regex(
    """S\d{1,2}|Season\s*\d{1,2}|\d{1,2}(?:st|nd|rd|th)\s*Season|II|III|IV|第\s*[\d一二三四五六七八九十]+\s*[季期部]|[\d一二三四五六七八九十]+\s*[季期]|""" +
            """剧场版|劇場版|电影|電影|Movie|OVA|OAD|SP|特别篇|特別篇|总集篇|總集篇|番外篇?""",
    RegexOption.IGNORE_CASE,
)

private val TITLE_WORD_CHAR = Regex("""[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\u3005\u3007\u3040-\u30FF\u31F0-\u31FF\uAC00-\uD7AF\uFF66-\uFF9FA-Za-z]""")

private const val TITLE_TAG_WORD = """(?:\d{3,4}\s*[pPiI]|\d{3,4}\s*[xX×]\s*\d{3,4}|4K|x264|x265|hevc|avc|aac|flac|opus|webrip|web-dl|bdrip|bd|dvdrip|baha|crunchyroll|bilibili|b-global|netflix|10bit|8bit|hdr|mp4|mkv|gb|big5|cht|chs|jp|jpn|eng|ass|srt|""" +
        """简繁|简体|繁体|簡體|繁體|簡繁|简日|繁日|簡日|简日双语|繁日双语|简繁日多语|日语中字|中字|内封|内嵌|外挂|外掛|简繁内封|简繁外挂|簡繁外掛|简繁日|招募.*|合集|全集|完结|完|先行版本?|先行|修正.*|fin|end|ver\.?\s*\d+|v\d+.*|\d+\s*月\s*新番.*|""" +
        """.*(?:字幕组|字幕社|字幕組|工作室|制作组|压制|發佈|发布|搬运组|搬運組|raws|fansub|sub))"""

private val TITLE_TAG_WORDS = Regex(
    """\s*(?:(?-i:(?![A-Z]{5,}$)[A-Z0-9_\-+&.\s]+)|$TITLE_TAG_WORD(?:[\s_&+\-]+$TITLE_TAG_WORD)*)\s*""",
    RegexOption.IGNORE_CASE,
)
