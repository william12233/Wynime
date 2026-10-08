package com.wynime.app.domain.sourceplugin

import com.wynime.app.data.network.traditionalToSimplifiedChinese

private val numberedSourceTitlePattern = Regex(
    """^(.+?)\s*第\s*([零〇一二两兩三四五六七八九十百千0-9]+)\s*(季|部)(?:\s+.*)?$""",
)
private val ordinalSourceTitlePattern = Regex(
    """(?i)^(.+?)\s*(\d+)(?:st|nd|rd|th)\s+season(?:\s+.*)?$""",
)
private val seasonSourceTitlePattern = Regex(
    """(?i)^(.+?)\s+season\s*(\d+)(?:\s+.*)?$""",
)
private val compactSourceTitlePattern = Regex("^(.+?)(\\d{1,2})$")
private val coverSuffixPattern = Regex("(?i)\\s*(?:封面图|封面圖)$")

private val specialSourceTitleMarkers = listOf(
    "season",
    "part",
    "ova",
    "oad",
    "剧场版",
    "劇場版",
    "电影",
    "電影",
    "movie",
    "日记",
    "日記",
    "外传",
    "外傳",
    "特别篇",
    "特別篇",
    "冰结之绊",
    "冰結之絆",
    "雪之回忆",
    "雪之回憶",
    "新编集版",
    "新編集版",
    "新编辑版",
)

internal enum class SourceTitleVariantKind {
    SEASON,
    PART,
}

internal data class SourceTitleVariant(
    val kind: SourceTitleVariantKind,
    val number: Int,
)

internal data class SourceTitleMatch(
    val canonical: String,
    val base: String,
    val baseTitle: String,
    val variant: SourceTitleVariant?,
    val hasVariantMarker: Boolean,
    val isBaseEquivalent: Boolean,
)

/**
 * Builds a bounded set of simplified queries for sites with inconsistent title indexing.
 * The base title is first because several sites index seasonal entries under the franchise name.
 */
internal fun sourceSearchQueryVariants(query: String): List<String> {
    val simplified = normalizeSourceQuery(query)
    if (simplified.isBlank()) return listOf(query)

    val title = sourceTitleMatch(simplified)
    val result = linkedSetOf<String>()
    fun add(value: String) {
        value.trim().takeIf(String::isNotBlank)?.let(result::add)
    }

    val variant = title.variant
    if (variant == null) {
        add(simplified)
    } else {
        add(title.baseTitle)
        add(simplified)
        add("${title.baseTitle}第${variant.number}${variant.kind.suffix()}")
        add("${title.baseTitle}${variant.number}")
    }
    return result.toList()
}

internal fun sourceTitleMatch(value: String): SourceTitleMatch {
    val normalized = normalizeSourceQuery(value).replace(coverSuffixPattern, "").trim()
    val parsedVariant = parseSourceTitleVariant(normalized)
    val baseTitle = parsedVariant?.baseTitle ?: normalized
    val base = baseTitle.filter(Char::isLetterOrDigit)
    val hasSpecialMarker = specialSourceTitleMarkers.any(normalized::contains)
    val canonical = if (parsedVariant == null) {
        base
    } else {
        "$base#${parsedVariant.variant.kind.name.lowercase()}:${parsedVariant.variant.number}"
    }
    return SourceTitleMatch(
        canonical = canonical,
        base = base,
        baseTitle = baseTitle,
        variant = parsedVariant?.variant,
        hasVariantMarker = parsedVariant != null || hasSpecialMarker,
        isBaseEquivalent = parsedVariant?.variant?.number == 1 ||
            normalized.contains("新编集版") ||
            normalized.contains("新編集版") ||
            normalized.contains("新编辑版"),
    )
}

private data class ParsedSourceTitleVariant(
    val baseTitle: String,
    val variant: SourceTitleVariant,
)

private fun parseSourceTitleVariant(value: String): ParsedSourceTitleVariant? {
    numberedSourceTitlePattern.matchEntire(value)?.let { match ->
        val number = parseSourceTitleNumber(match.groupValues[2]) ?: return@let null
        val kind = when (match.groupValues[3]) {
            "季" -> SourceTitleVariantKind.SEASON
            else -> SourceTitleVariantKind.PART
        }
        return ParsedSourceTitleVariant(
            baseTitle = match.groupValues[1].trim(),
            variant = SourceTitleVariant(kind, number),
        )
    }
    ordinalSourceTitlePattern.matchEntire(value)?.let { match ->
        val number = match.groupValues[2].toIntOrNull() ?: return@let null
        return ParsedSourceTitleVariant(
            baseTitle = match.groupValues[1].trim(),
            variant = SourceTitleVariant(SourceTitleVariantKind.SEASON, number),
        )
    }
    seasonSourceTitlePattern.matchEntire(value)?.let { match ->
        val number = match.groupValues[2].toIntOrNull() ?: return@let null
        return ParsedSourceTitleVariant(
            baseTitle = match.groupValues[1].trim(),
            variant = SourceTitleVariant(SourceTitleVariantKind.SEASON, number),
        )
    }
    compactSourceTitlePattern.matchEntire(value)?.let { match ->
        val baseTitle = match.groupValues[1].trim()
        val number = match.groupValues[2].toIntOrNull()
        if (number != null && baseTitle.any(::isCjkCharacter)) {
            return ParsedSourceTitleVariant(
                baseTitle = baseTitle,
                variant = SourceTitleVariant(SourceTitleVariantKind.SEASON, number),
            )
        }
    }
    return null
}

private fun parseSourceTitleNumber(value: String): Int? = value.toIntOrNull() ?: run {
    var total = 0
    var number = 0
    for (character in value) {
        val digit = when (character) {
            '零', '〇' -> 0
            '一' -> 1
            '二', '两', '兩' -> 2
            '三' -> 3
            '四' -> 4
            '五' -> 5
            '六' -> 6
            '七' -> 7
            '八' -> 8
            '九' -> 9
            else -> null
        }
        val unit = when (character) {
            '十' -> 10
            '百' -> 100
            '千' -> 1_000
            else -> null
        }
        when {
            digit != null -> number = digit
            unit != null -> {
                total += (if (number == 0) 1 else number) * unit
                number = 0
            }
            else -> return@run null
        }
    }
    (total + number).takeIf { it > 0 }
}

private fun normalizeSourceQuery(value: String): String =
    traditionalToSimplifiedChinese(value)
        .lowercase()
        .replace("：", ":")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun isCjkCharacter(character: Char): Boolean =
    character in '\u3400'..'\u9fff'

private fun SourceTitleVariantKind.suffix(): String = when (this) {
    SourceTitleVariantKind.SEASON -> "季"
    SourceTitleVariantKind.PART -> "部"
}
