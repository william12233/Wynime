package com.wynime.app.domain.mediasource

import androidx.collection.IntSet
import androidx.collection.MutableIntSet
import com.wynime.app.domain.mediasource.MediaListFilters.charsToDelete
import com.wynime.app.domain.mediasource.MediaListFilters.charsToReplaceWithWhitespace
import com.wynime.app.domain.mediasource.MediaListFilters.keepWords
import com.wynime.app.domain.mediasource.MediaListFilters.minimumLength
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.contains
import com.wynime.utils.platform.deleteAnyCharIn
import com.wynime.utils.platform.deleteInfix
import com.wynime.utils.platform.deletePrefix
import com.wynime.utils.platform.replaceMatches
import com.wynime.utils.platform.trimSB

object MediaListFilters {

    val ContainsSubjectName = BasicMediaListFilter { media ->
        subjectNamesWithoutSpecial.any { subjectName ->
            val originalTitle = removeSpecials(media.subjectName, removeWhitespace = true, replaceNumbers = true)
            fun exactlyContains() = originalTitle
                .contains(subjectName, ignoreCase = true)

            fun fuzzyMatches() = StringMatcher.calculateMatchRate(originalTitle, subjectName) >= 80

            exactlyContains() || fuzzyMatches()
        }
    }

    val ContainsEpisodeSort = BasicMediaListFilter { media ->
        val range = media.episodeRange ?: return@BasicMediaListFilter false
        range.contains(episodeSort)
    }
    val ContainsEpisodeEp = BasicMediaListFilter { media ->
        val range = media.episodeRange ?: return@BasicMediaListFilter false
        episodeEp != null && range.contains(episodeEp)
    }

    val ContainsEpisodeName = BasicMediaListFilter { media ->
        episodeName ?: return@BasicMediaListFilter false
        if (episodeSort is EpisodeSort.Normal) {
            return@BasicMediaListFilter false
        }
        val name = episodeNameForCompare
        checkNotNull(name)
        if (name.isBlank()) return@BasicMediaListFilter false
        removeSpecials(media.originalTitle, removeWhitespace = true, replaceNumbers = true)
            .contains(name, ignoreCase = true)
    }

    val ContainsAnyEpisodeInfo = ContainsEpisodeSort or ContainsEpisodeName or ContainsEpisodeEp

    private val numberMappings = buildMap {
        put("X", "10")
        put("IX", "9")
        put("VIII", "8")
        put("VII", "7")
        put("VI", "6")
        put("V", "5")
        put("IV", "4")
        put("III", "3")
        put("II", "2")
        put("I", "1")

        put("十", "10")
        put("九", "9")
        put("八", "8")
        put("七", "7")
        put("六", "6")
        put("五", "5")
        put("四", "4")
        put("三", "3")
        put("二", "2")
        put("一", "1")
    }

    private val minimumLength: Int = 2
    private val allNumbersRegex = numberMappings.keys.joinToString("|").toRegex()

    val charsToDelete = """""".toCharCodeIntSet()
    val charsToDeleteForSearch get() = charsToReplaceWithWhitespace

    private val charsToReplaceWithWhitespace = """。、，·・[]～“”~—-!@#$%^&*()_+{}|\;':",.<>/?【】：「」！""".toCharCodeIntSet()
    private val whitespaceChars = " \t\u3000".toCharCodeIntSet()

    private data class KeepWords(
        val originalWord: String,
        val mask: String
    )

    private val keepWords = listOf("Re：").mapIndexed { index, s ->
        KeepWords(s, "\uE001$index\uE002")
    }

    fun removeSpecials(
        string: String,
        removeWhitespace: Boolean,
        replaceNumbers: Boolean,
        removeMarkers: Boolean = false,
    ): String {

        var result = keepWords.fold(string) { acc, keepWord ->
            acc.replace(keepWord.originalWord, keepWord.mask)
        }

        val sb = StringBuilder(result).apply {
            if (removeMarkers) {
                deletePrefix("电影")
                deleteInfix("电影")
                deletePrefix("剧场版")
                deleteInfix("剧场版")
                deletePrefix("OVA")
                deleteInfix("OVA")
                deleteInfix("OAD")
                deleteInfix("总集篇")
            }
        }

        val processed = applyConditionalRules(sb.toString())

        val afterNumberReplace = if (replaceNumbers) {
            StringBuilder(processed).replaceMatches(allNumbersRegex) { match ->
                replaceNumberConditional(processed, match)
            }.toString()
        } else {
            processed
        }

        val sb2 = StringBuilder(afterNumberReplace)
        if (removeWhitespace) {
            sb2.deleteAnyCharIn(whitespaceChars)
        }
        sb2.trimSB()

        result = sb2.toString()
        result = keepWords.fold(result) { acc, keepWord ->
            acc.replace(keepWord.mask, keepWord.originalWord)
        }

        return result
    }

    fun specialEquals(first: String, second: String): Boolean {
        return removeSpecials(first, removeWhitespace = true, replaceNumbers = true)
            .equals(removeSpecials(second, removeWhitespace = true, replaceNumbers = true), ignoreCase = true)
    }

    fun specialContains(string: String, sub: String): Boolean {
        return removeSpecials(string, removeWhitespace = true, replaceNumbers = true)
            .contains(removeSpecials(sub, removeWhitespace = true, replaceNumbers = true), ignoreCase = true)
    }

    private fun applyConditionalRules(original: String): String {
        val sbResult = StringBuilder()
        var nonSpecialCount = 0

        var canProcess = false

        for (c in original) {
            if (c.isSpecialChar()) {
                val code = c.code
                if (nonSpecialCount == 0) {

                    if (charsToDelete.contains(code)) {

                    } else if (charsToReplaceWithWhitespace.contains(code)) {

                        sbResult.append(' ')
                    } else {

                        sbResult.append(c)
                    }
                } else {
                    if (canProcess) {

                        if (charsToDelete.contains(code)) {

                        } else if (charsToReplaceWithWhitespace.contains(code)) {
                            sbResult.append(' ')
                        } else {
                            sbResult.append(c)
                        }
                    } else {

                        sbResult.append(c)
                    }
                }
            } else {

                sbResult.append(c)
                nonSpecialCount++
                if (!canProcess && nonSpecialCount >= minimumLength) {

                    canProcess = true
                }
            }
        }

        return sbResult.toString()
    }

    private fun replaceNumberConditional(original: String, match: MatchResult): String {

        if (original.isLetterOrDigit(match.range.first - 1) ||
            original.isLetterOrDigit(match.range.last + 1)
        ) {
            if (match.value.isChineseNumber()) {

                if (match.range.first == 0 && original.isChineseCharacter(match.range.last + 1)) {
                    return match.value
                }
                if (match.range.first > 0 && match.range.last < original.lastIndex) {
                    if (original[match.range.first - 1] != '第') {
                        return match.value
                    }
                }
            } else if (original.isJapaneseCharacter(match.range.first - 1)) {

            } else {

                return match.value
            }

        }

        if (match.value == "V") {
            val prevLetter = original.reverseFirstLetterOrDigit(match.range.first - 1)
            val nextLetter = original.traverseFirstLetterOrDigit(match.range.last + 1)
            if ((prevLetter == 'O' || prevLetter == 'o') &&
                (nextLetter == 'A' || nextLetter == 'a')
            ) {
                return match.value
            }
        }

        return numberMappings[match.value] ?: match.value
    }

    private fun Char.isSpecialChar(): Boolean {
        val code = code
        return charsToDelete.contains(code) || charsToReplaceWithWhitespace.contains(code)
    }
}

private fun String.toCharCodeIntSet(): IntSet {
    val chars = toCharArray()
    return MutableIntSet(chars.size).apply {
        chars.forEach { add(it.code) }
    }
}

private fun String.isLetterOrDigit(index: Int): Boolean {
    return getOrNull(index)?.isLetterOrDigit() == true
}

private fun String.isChineseCharacter(index: Int): Boolean {
    return getOrNull(index)?.code in 0x4E00..0x9FFF
}

private fun String.isJapaneseCharacter(index: Int): Boolean {
    val code = getOrNull(index)?.code ?: return false
    return (code in 0x3040..0x309F) ||
            (code in 0x30A0..0x30FF) ||
            (code in 0x4E00..0x9FBF)
}

private fun String.isChineseNumber(): Boolean {
    if (length != 1) return false
    return first() in '一'..'十'
}

private fun String.reverseFirstLetterOrDigit(index: Int): Char? {
    if (index !in 0..<length) return null
    for (i in index downTo 0) {
        val c = get(i)
        if (c.isLetterOrDigit()) {
            return c
        }
    }
    return null
}

private fun String.traverseFirstLetterOrDigit(index: Int): Char? {
    if (index !in 0..<length) return null
    for (i in index until length) {
        val c = get(i)
        if (c.isLetterOrDigit()) {
            return c
        }
    }
    return null
}