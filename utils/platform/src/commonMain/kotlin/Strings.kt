package com.wynime.utils.platform

import androidx.collection.IntSet
import kotlin.math.round

fun String.Companion.format1f(value: Double): String {
    return (round(value * 10) / 10.0).toString()
}

fun String.Companion.format1f(value: Float): String {
    return (round(value * 10) / 10.0).toString()
}

fun String.Companion.format2f(value: Double): String {
    return (round(value * 100) / 100.0).toString()
}

fun String.Companion.format2f(value: Float): String {
    return (round(value * 100) / 100.0).toString()
}

fun StringBuilder.deleteMatches(regex: Regex): StringBuilder {
    var matchResult = regex.find(this)
    while (matchResult != null) {
        deleteRange(matchResult.range.first, matchResult.range.last + 1)
        matchResult = regex.find(this, matchResult.range.first)
    }
    return this
}

fun StringBuilder.deleteAnyCharIn(chars: IntSet): StringBuilder {
    var index = 0
    while (index < length) {
        if (this[index].code in chars) {
            deleteAt(index)
        } else {
            index++
        }
    }
    return this
}

fun StringBuilder.deletePrefix(prefix: String): StringBuilder {
    if (startsWith(prefix)) {
        deleteRange(0, prefix.length)
    }
    return this
}

fun StringBuilder.deleteSuffix(suffix: String): StringBuilder {
    if (endsWith(suffix)) {
        deleteRange(length - suffix.length, length)
    }
    return this
}

fun StringBuilder.deleteInfix(infix: String): StringBuilder {
    val index = indexOf(infix)
    if (index != -1) {
        deleteRange(index, index + infix.length)
    }
    return this
}

inline fun StringBuilder.replaceMatches(regex: Regex, replacement: (MatchResult) -> String): StringBuilder {
    val matches = regex.findAll(this)
    val result = StringBuilder()
    var lastEnd = 0
    for (match in matches) {
        result.append(substring(lastEnd, match.range.first))
        result.append(replacement(match))
        lastEnd = match.range.last + 1
    }
    result.append(substring(lastEnd))
    clear().append(result)
    return this
}

fun StringBuilder.trimSB(): StringBuilder {
    var start = 0
    while (start < length && this[start].isWhitespace()) {
        start++
    }
    deleteRange(0, start)
    var end = length
    while (end > 0 && this[end - 1].isWhitespace()) {
        end--
    }
    deleteRange(end, length)
    return this
}
