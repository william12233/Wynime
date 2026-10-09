package com.wynime.app.domain.sourceplugin

import com.wynime.app.data.network.simplifyChineseOrOriginal

private val numberedSourceTitlePattern = Regex(
    """^(.+?)\s*第\s*([零〇一二两兩三四五六七八九十百千0-9]+)\s*(季|部)(?:\s+.*)?$""",
)
private val ordinalSourceTitlePattern = Regex(
    """(?i)^(.+?)\s*(\d+)(?:st|nd|rd|th)\s+season(?:\s+.*)?$""",
)
private val seasonSourceTitlePattern = Regex(
    """(?i)^(.+?)\s+season\s*(\d+)(?:\s+.*)?$""",
)
private val partSourceTitlePattern = Regex(
    """(?i)^(.+?)\s+part\s*\.?\s*(\d+)(?:\s+.*)?$""",
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

private val sourceTitleBaseSuffixPattern = Regex(
    """(?i)^(.+?)\s*(?:u\s*[- ]?\s*\d+|world\s*cup|world\s*championship|世界杯|世界盃|semi[- ]?final|半决赛|半決賽|準決勝|準決賽|(?:the\s+)?final\s+season|最终季|最終季|season\s*\d+|part(?:[.\s]*\d+)?|ova|oad|剧场版|劇場版|电影|電影|特别篇|特別篇).*$""",
)

private val sourceTitleMarkerPatterns = listOf(
    "semifinal" to Regex("(?i)semi[- ]?final|半决赛|半決賽|準決勝|準決賽"),
    "worldcup" to Regex("(?i)world[ -]?cup|世界杯|世界盃"),
    "finalseason" to Regex("""(?i)final\s+season|最终季|最終季"""),
    "movie" to Regex("(?i)movie|剧场版|劇場版|电影|電影"),
    "ova" to Regex("(?i)ova|oad"),
    "special" to Regex("特别篇|特別篇|番外篇|番外"),
)
private val arcSourceTitleMarkerNames = setOf("semifinal", "worldcup", "finalseason")

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
    val arcMarkers: Set<String>,
)

/**
 * Builds a bounded set of outbound queries for sites with inconsistent title indexing.
 * The original spelling is kept before its simplified equivalent so a traditional-only
 * index cannot be made unreachable by the normalizer.
 */
internal fun sourceSearchQueryVariants(query: String): List<String> {
    val original = normalizeSourceSurface(query)
    val simplified = normalizeSourceQuery(query)
    if (original.isBlank() && simplified.isBlank()) return listOf(query)

    val title = sourceTitleMatch(simplified)
    val originalTitle = parseSourceTitleVariant(original)
    val result = linkedSetOf<String>()
    fun add(value: String) {
        value.trim().takeIf(String::isNotBlank)?.let(result::add)
    }

    val variant = title.variant
    if (variant == null) {
        add(original)
        add(simplified)
        sourceTitleBaseFallbacks(original).forEach(::add)
        sourceTitleBaseFallbacks(simplified).forEach(::add)
    } else {
        val originalBaseTitle = originalTitle?.baseTitle ?: original
        add(originalBaseTitle)
        add(title.baseTitle)
        add(original)
        add(simplified)
        add("${originalBaseTitle}第${variant.number}${variant.kind.suffix()}")
        add("${title.baseTitle}第${variant.number}${variant.kind.suffix()}")
        add("${originalBaseTitle}${variant.number}")
        add("${title.baseTitle}${variant.number}")
        sourceTitleBaseFallbacks(original).forEach(::add)
        sourceTitleBaseFallbacks(simplified).forEach(::add)
    }
    return result.toList()
}

/**
 * Merges title spellings from all known subject names before querying a provider.
 * The individual title matcher remains responsible for deciding whether a result
 * belongs to the requested installment.
 */
internal fun sourceSearchQueryVariantsForRequest(
    queryNames: List<String>,
): List<String> {
    val result = linkedSetOf<String>()
    queryNames.flatMap(::sourceSearchQueryVariants).forEach(result::add)
    return result.toList()
}

/**
 * Keeps the source selector aware of every canonical title supplied by Bangumi.
 */
internal fun sourceTitleNamesForRequest(
    queryNames: List<String>,
): List<String> = queryNames.map(String::trim).filter(String::isNotBlank).distinct()

/**
 * Returns the season-specific spellings of a title without including its franchise-only query.
 *
 * The source search needs the franchise spelling as a fallback, while the selector must not
 * treat that fallback as another season. Keeping this set separate lets both paths share the
 * same season parser without weakening season filtering.
 */
internal fun sourceTitleSeasonVariants(query: String): List<String> {
    val variant = sourceTitleMatch(query).variant ?: return emptyList()
    return sourceSearchQueryVariants(query).filter { candidate ->
        sourceTitleMatch(candidate).variant == variant
    }
}

internal fun sourceTitleMatch(value: String): SourceTitleMatch {
    val normalized = normalizeSourceQuery(value).replace(coverSuffixPattern, "").trim()
    val parsedVariant = parseSourceTitleVariant(normalized)
    val baseTitle = parsedVariant?.baseTitle ?: normalized
    val base = normalizeSourceTitleKey(baseTitle)
    val hasSpecialMarker = specialSourceTitleMarkers.any(normalized::contains)
    val titleMarkers = sourceTitleMarkerPatterns
        .filter { (_, pattern) -> pattern.containsMatchIn(normalized) }
        .mapTo(linkedSetOf()) { (marker, _) -> marker }
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
        hasVariantMarker = parsedVariant != null || hasSpecialMarker || titleMarkers.isNotEmpty(),
        isBaseEquivalent = parsedVariant?.variant?.number == 1 ||
            normalized.contains("新编集版") ||
            normalized.contains("新編集版") ||
            normalized.contains("新编辑版"),
        arcMarkers = titleMarkers.filterTo(linkedSetOf()) { it in arcSourceTitleMarkerNames },
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
    partSourceTitlePattern.matchEntire(value)?.let { match ->
        val number = match.groupValues[2].toIntOrNull() ?: return@let null
        return ParsedSourceTitleVariant(
            baseTitle = match.groupValues[1].trim(),
            variant = SourceTitleVariant(SourceTitleVariantKind.PART, number),
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

private fun sourceTitleBaseFallbacks(value: String): List<String> {
    val normalized = normalizeSourceSurface(value)
    val result = linkedSetOf<String>()
    sourceTitleBaseSuffixPattern.matchEntire(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.length >= 2 && it.any(Char::isLetterOrDigit) }
        ?.let(result::add)

    normalized.split(Regex("\\s*[:：/／|｜]\\s*"), limit = 2)
        .firstOrNull()
        ?.trim()
        ?.takeIf { it.length >= 2 && it != normalized && it.any(Char::isLetterOrDigit) }
        ?.let(result::add)

    return result.toList()
}

private fun normalizeSourceTitleKey(value: String): String = value
    .let { normalizeSourceQuery(it) }
    .replace(Regex("(?i)semi[- ]?final"), "semifinal")
    .replace("半決賽", "semifinal")
    .replace("半决赛", "semifinal")
    .replace("準決勝", "semifinal")
    .replace("準決賽", "semifinal")
    .replace(Regex("(?i)world[ -]?cup"), "worldcup")
    .replace("世界盃", "worldcup")
    .replace("世界杯", "worldcup")
    .replace(Regex("""(?i)(?:the\s+)?final\s+season"""), "finalseason")
    .replace("最终季", "finalseason")
    .replace("最終季", "finalseason")
    .filter(Char::isLetterOrDigit)

private fun normalizeSourceSurface(value: String): String =
    value
        .lowercase()
        .replace("：", ":")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun normalizeSourceQuery(value: String): String =
    simplifyChineseOrOriginal(normalizeSourceSurface(value))

private fun isCjkCharacter(character: Char): Boolean =
    character in '\u3400'..'\u9fff'

private fun SourceTitleVariantKind.suffix(): String = when (this) {
    SourceTitleVariantKind.SEASON -> "季"
    SourceTitleVariantKind.PART -> "部"
}
