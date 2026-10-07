package com.wynime.datasources.api.title

import com.wynime.datasources.api.topic.titles.ParsedTopicTitle
import com.wynime.datasources.api.topic.titles.RawTitleParser
import com.wynime.datasources.api.topic.titles.parse

abstract class PatternBasedTitleParserTestSuite {
    private val parser = RawTitleParser.getDefault()

    fun parse(text: String): ParsedTopicTitle = parser.parse(text, allianceName = null)
}
