package com.wynime.utils.xml

import kotlinx.io.Source
import kotlinx.io.asInputStream
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

actual object Xml {
    actual fun parse(string: String, baseUrl: String): org.jsoup.nodes.Document =
        Jsoup.parse(string, baseUrl, Parser.xmlParser())

    actual fun parse(source: Source, baseUrl: String): Document =
        Jsoup.parse(source.asInputStream(), "UTF-8", baseUrl, Parser.xmlParser())

    actual fun parse(string: String): Document {
        return Jsoup.parse(string, Parser.xmlParser())
    }

    actual fun parse(source: Source): Document {
        return Jsoup.parse(source.asInputStream(), "UTF-8", "", Parser.xmlParser())
    }
}

actual object QueryParser {
    @Throws(IllegalStateException::class)
    actual fun parseSelector(selector: String): Evaluator {
        return org.jsoup.select.QueryParser.parse(selector)
    }
}

actual object Html {
    actual fun parse(string: String): Document {
        return Jsoup.parse(string)
    }

    actual fun parse(string: String, baseUrl: String): Document {
        return Jsoup.parse(string, baseUrl)
    }

    actual fun parse(source: Source): Document {
        return Jsoup.parse(source.asInputStream(), "UTF-8", "")
    }

    actual fun parse(source: Source, baseUrl: String): Document {
        return Jsoup.parse(source.asInputStream(), "UTF-8", baseUrl)
    }
}