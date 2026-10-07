package com.wynime.utils.xml

import kotlinx.io.Source

expect object Xml {
    fun parse(string: String): Document
    fun parse(string: String, baseUrl: String): Document
    fun parse(source: Source): Document
    fun parse(source: Source, baseUrl: String): Document
}

expect object Html {
    fun parse(string: String): Document
    fun parse(string: String, baseUrl: String): Document
    fun parse(source: Source): Document
    fun parse(source: Source, baseUrl: String): Document
}

expect object QueryParser {
    @Throws(IllegalStateException::class)
    fun parseSelector(selector: String): Evaluator
}

fun QueryParser.parseSelectorOrNull(selector: String): Evaluator? {
    if (selector.isBlank()) return null
    return try {
        parseSelector(selector)
    } catch (e: IllegalStateException) {
        null
    }
}
