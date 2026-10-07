package com.wynime.app.data.models.preference

fun parseMpvOptions(lines: List<String>): Map<String, String> {
    return lines.mapNotNull { parseMpvOptionLine(it) }.toMap()
}

fun splitMpvOptionLines(text: String): List<String> {
    return text.lines()
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
}

private fun parseMpvOptionLine(line: String): Pair<String, String>? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

    val separatorIndex = trimmed.indexOf('=')
    val rawKey = if (separatorIndex == -1) trimmed else trimmed.substring(0, separatorIndex)
    var key = rawKey.trim().removePrefix("--")
    if (key.isEmpty()) return null

    val value = if (separatorIndex == -1) {

        if (key.length > 3 && key.startsWith("no-")) {
            key = key.removePrefix("no-")
            "no"
        } else {
            "yes"
        }
    } else {
        trimmed.substring(separatorIndex + 1).trim().removeSurroundingQuotes()
    }

    return key to value
}

private fun String.removeSurroundingQuotes(): String {
    if (length < 2) return this
    val first = first()
    if ((first == '"' || first == '\'') && last() == first) {
        return substring(1, length - 1)
    }
    return this
}
