package com.wynime.datasources.api.topic.titles

import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FrameRate
import com.wynime.datasources.api.topic.MediaOrigin
import com.wynime.datasources.api.topic.Resolution
import com.wynime.datasources.api.topic.SubtitleLanguage
import com.wynime.datasources.api.topic.TopicDetails

abstract class RawTitleParser {

    abstract fun parse(
        text: String,
        allianceName: String? = null,
        builder: ParsedTopicTitle.Builder,
    )

    open fun parseSubtitleLanguages(word: String): List<SubtitleLanguage> = parse(word).subtitleLanguages

    companion object {
        private val default by lazy(LazyThreadSafetyMode.PUBLICATION) {
            LabelFirstRawTitleParser()
        }

        fun getDefault(): RawTitleParser = default
    }
}

data class ParsedTopicTitle(
    val tags: List<String> = listOf(),
    val chineseTitle: String? = null,
    val otherTitles: List<String> = listOf(),
    val episodeRange: EpisodeRange? = null,
    val resolution: Resolution? = null,
    val frameRate: FrameRate? = null,
    val mediaOrigin: MediaOrigin? = null,
    val subtitleLanguages: List<SubtitleLanguage> = listOf(),
    val subtitleKind: SubtitleKind? = null,
) {
    class Builder {
        var tags = mutableSetOf<String>()
        var chineseTitle: String? = null
        var otherTitles = mutableSetOf<String>()
        var episodeRange: EpisodeRange? = null
        var resolution: Resolution? = null
        var frameRate: FrameRate? = null
        var mediaOrigin: MediaOrigin? = null
        var subtitleLanguages = mutableSetOf<SubtitleLanguage>()
        var subtitleKind: SubtitleKind? = null

        fun build(): ParsedTopicTitle {
            return ParsedTopicTitle(
                tags = tags.toList(),
                chineseTitle = chineseTitle?.trim(),
                otherTitles = otherTitles.mapNotNull { title -> title.trim().takeIf { it.isNotEmpty() } },
                episodeRange = episodeRange,
                resolution = resolution,
                frameRate = frameRate,
                mediaOrigin = mediaOrigin,
                subtitleLanguages = subtitleLanguages.toList(),
                subtitleKind = subtitleKind,
            )
        }
    }
}

fun RawTitleParser.parse(text: String, allianceName: String? = null): ParsedTopicTitle {
    return ParsedTopicTitle.Builder().run {
        parse(text, allianceName, this)
        build()
    }
}

fun ParsedTopicTitle.toTopicDetails(): TopicDetails {
    return TopicDetails(
        tags = tags,
        chineseTitle = chineseTitle,
        otherTitles = otherTitles,
        episodeRange = episodeRange,
        resolution = resolution,
        frameRate = frameRate,
        mediaOrigin = mediaOrigin,
        subtitleLanguages = subtitleLanguages,
        subtitleKind = subtitleKind,
    )
}