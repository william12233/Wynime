package com.wynime.datasources.api.topic.titles

import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FrameRate
import com.wynime.datasources.api.topic.MediaOrigin
import com.wynime.datasources.api.topic.Resolution
import com.wynime.datasources.api.topic.SubtitleLanguage

class PatternBasedRawTitleParser : RawTitleParser() {
    private val brackets = Regex("""[\[【(](.*?)[]】)]""")
    private val newAnime = Regex("(?:★?|★(.*)?)([0-9]|[一二三四五六七八九十]{0,4}) ?[月年] ?(?:新番|日剧)★?")
    private val specialEpisode = Regex("★特别篇")
    private val excludeTags = arrayOf(newAnime, specialEpisode, Regex("(短篇动画)|(招募)"))

    override fun parse(text: String, allianceName: String?, builder: ParsedTopicTitle.Builder) {
        return parse(
            text = text,
            allianceName = allianceName,
            collectTag = { builder.tags.add(it) },
            collectChineseTitle = { builder.chineseTitle = it },
            collectOtherTitle = { builder.otherTitles.add(it) },
            collectEpisode = { builder.episodeRange = it },
            collectResolution = { builder.resolution = it },
            collectFrameRate = { builder.frameRate = it },
            collectMediaOrigin = { builder.mediaOrigin = it },
            collectSubtitleLanguage = { builder.subtitleLanguages.add(it) },
        )
    }

    fun parse(
        text: String,
        allianceName: String?,
        collectTag: (title: String) -> Unit,
        collectChineseTitle: (String) -> Unit,
        collectOtherTitle: (String) -> Unit,
        collectEpisode: (EpisodeRange) -> Unit,
        collectResolution: (Resolution) -> Unit,
        collectFrameRate: (FrameRate) -> Unit,
        collectMediaOrigin: (MediaOrigin) -> Unit,
        collectSubtitleLanguage: (SubtitleLanguage) -> Unit,
    ) {
        val exceptTagsBuilder = StringBuilder()
        var index = 0
        var unknownTags = mutableListOf<String>()
        for (result in brackets.findAll(text)) {
            if (index < result.range.first) {
                exceptTagsBuilder.append(text.subSequence(index until result.range.first))
            }
            index = result.range.last + 1

            val tagOrTags = result.groups[1]!!.value
            for (tag in splitTags(tagOrTags)) {
                val anyMatched = processTag(
                    tag = tag,
                    collectSubtitleLanguage = collectSubtitleLanguage,
                    collectResolution = collectResolution,
                    collectFrameRate = collectFrameRate,
                    collectMediaOrigin = collectMediaOrigin,
                    collectEpisode = {
                        if (allianceName == "天使动漫论坛") return@processTag

                        collectEpisode(it)

                    },
                )

                if (!anyMatched) {
                    unknownTags.add(tag.trim())
                }
            }
        }
        if (index < text.length) {
            exceptTagsBuilder.append(text.subSequence(index until text.length))
        }
        unknownTags = unknownTags.filterNotTo(mutableListOf()) { tag -> excludeTags.any { it.find(tag) != null } }
        unknownTags.removeFirstOrNull()

        val exceptTags = exceptTagsBuilder.toString()
            .replace(newAnime) { "" }
            .replace(specialEpisode) { "" }
        if (exceptTags.isBlank() || allianceName == "极影字幕社" || text.contains("沸羊羊")) {

            val primaryTitles = unknownTags.removeFirstOrNull()
                ?: return
            var collectedOtherTitle = false
            while (unknownTags.isNotEmpty()) {
                val name = unknownTags.removeAt(0)
                if (name.count { it == ' ' } > 2) {
                    collectOtherTitle(name)
                    collectedOtherTitle = true
                } else {
                    unknownTags.add(0, name)
                    break
                }
            }

            if (collectedOtherTitle) {

                collectChineseTitle(primaryTitles)
            } else {
                parseNames(primaryTitles, collectChineseTitle, collectOtherTitle)
            }

            for (unknownTag in unknownTags) {
                collectTag(unknownTag)
            }
        } else {

            for (unknownTag in unknownTags) {
                collectTag(unknownTag)
            }

            exceptTags.substringAfterLast('-', "").takeIf { it.isNotBlank() }?.trim()?.let { maybeEpisode ->
                if (maybeEpisode.contains("_")) {
                    maybeEpisode.splitToSequence("_").forEach {
                        processTag(
                            tag = it,
                            collectSubtitleLanguage = collectSubtitleLanguage,
                            collectResolution = collectResolution,
                            collectFrameRate = collectFrameRate,
                            collectMediaOrigin = collectMediaOrigin,
                            collectEpisode = collectEpisode,
                        )
                    }
                } else {
                    collectEpisode(EpisodeRange.single(EpisodeSort(maybeEpisode)))
                }
            }

            parseNames(exceptTags, collectChineseTitle, collectOtherTitle)
        }

    }

    private fun splitTags(tagOrTags: String): Sequence<String> {
        return sequenceOf(tagOrTags)

    }

    private fun parseNames(
        string: String,
        collectChineseTitle: (String) -> Unit,
        collectOtherTitle: (String) -> Unit,
    ) {
        val names = string.substringBeforeLast('-').split('/', '\\', '-', '_')
        names.firstOrNull()?.let(collectChineseTitle)
        names.asSequence().drop(1).mapNotNull { it.trim().takeIf(String::isNotEmpty) }.forEach(collectOtherTitle)
    }

    private fun processTag(
        tag: String,
        collectSubtitleLanguage: (SubtitleLanguage) -> Unit,
        collectResolution: (Resolution) -> Unit,
        collectFrameRate: (FrameRate) -> Unit,
        collectMediaOrigin: (MediaOrigin) -> Unit,
        collectEpisode: (EpisodeRange) -> Unit,
    ): Boolean {
        var anyMatched = false
        anyMatched = anyMatched or tag.parseSubtitleLanguages(collectSubtitleLanguage)
        anyMatched = anyMatched or tag.parseResolution(collectResolution)
        anyMatched = anyMatched or tag.parseFrameRate(collectFrameRate)
        anyMatched = anyMatched or tag.parseMediaOrigin(collectMediaOrigin)
        anyMatched = anyMatched or tag.parseEpisode(collectEpisode)

        return anyMatched
    }

    private fun String.parseSubtitleLanguages(collect: (SubtitleLanguage) -> Unit): Boolean {
        var any = false
        for (entry in SubtitleLanguage.matchableEntries) {
            if (entry.matches(this)) {
                collect(entry)
                any = true
            }
        }
        return any
    }

    private fun String.parseResolution(collect: (Resolution) -> Unit): Boolean {
        return Resolution.tryParse(this)?.let(collect) != null
    }

    private fun String.parseFrameRate(collect: (FrameRate) -> Unit): Boolean {
        return FrameRate.tryParse(this)?.let(collect) != null
    }

    private fun String.parseMediaOrigin(collect: (MediaOrigin) -> Unit): Boolean {
        return MediaOrigin.tryParse(this)?.let(collect) != null
    }

    private val collectionKeywords = listOf("全集", "合集", "Fin", "END")
    private val collectionPattern = Regex("""(\d{2,4})\s?-\s?(\d{2,4})""")

    private fun String.parseEpisode(collectEpisode: (EpisodeRange) -> Unit): Boolean {
        this.toFloatOrNull()?.let {
            collectEpisode(EpisodeRange.single(this))
            return true
        }
        if (this.startsWith("第")) {
            collectEpisode(EpisodeRange.single(this.removePrefix("第").removeSuffix("话").removeSuffix("話")))
            return true
        }
        if (this.contains("SP", ignoreCase = true) || this.contains("小剧场")) {
            collectEpisode(EpisodeRange.single(this))
            return true
        }
        if (collectionKeywords.any { this.contains(it, ignoreCase = true) }) {

            collectionPattern.find(this)?.let {
                if (it.groupValues.size != 3) {
                    return@let
                }
                val (start, end) = it.destructured
                collectEpisode(EpisodeRange.range(start, end))
                return true
            }
        }
        return false
    }
}
