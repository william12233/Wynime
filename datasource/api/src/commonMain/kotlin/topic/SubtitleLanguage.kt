package com.wynime.datasources.api.topic

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(SubtitleLanguage.Serializer::class)
sealed class SubtitleLanguage(
    val id: String,
    val displayName: String,
) {
    abstract fun matches(text: String): Boolean
    override fun toString(): String = displayName

    sealed class Chinese(id: String, displayName: String) : SubtitleLanguage(id, displayName)

    object ChineseCantonese : Chinese("CHC", "粤语") {
        private val tokens = arrayOf("粤", "粵", "Cantonese", "CHC", "Yue")
        override fun matches(text: String): Boolean {
            return tokens.any { text.contains(it, ignoreCase = true) }
        }
    }

    object ChineseSimplified : Chinese("CHS", "简中") {
        private val tokens =
            arrayOf(
                "简中",
                "GB",
                "GBK",
                "简体中文",
                "中文",
                "中字",
                "簡",
                "简",
                "CHS",
                "Zh-Hans",
                "Zh_Hans",
                "zh_cn",
                "zh",
            )

        override fun matches(text: String): Boolean {
            if (text.contains("繁體中文")) return false
            return tokens.any { text.contains(it, ignoreCase = true) }
        }
    }

    object ChineseTraditional : Chinese("CHT", "繁中") {
        private val tokens = arrayOf("繁中", "BIG5", "BIG 5", "繁", "Chinese", "CHT", "TC")
        override fun matches(text: String): Boolean {
            return tokens.any { text.contains(it, ignoreCase = true) }
        }

    }

    object Japanese : SubtitleLanguage("JPN", "日语") {
        private val tokens = arrayOf("日", "Japanese", "JP")
        override fun matches(text: String): Boolean {
            return tokens.any { text.contains(it, ignoreCase = true) }
        }
    }

    object English : SubtitleLanguage("ENG", "英语") {
        private val tokens = arrayOf("英", "English")
        override fun matches(text: String): Boolean {
            return tokens.any { text.contains(it, ignoreCase = true) }
        }
    }

    class Other(
        displayName: String
    ) : SubtitleLanguage("Other", displayName) {
        override fun matches(text: String): Boolean {
            return true
        }
    }

    object ParseError : SubtitleLanguage("ERROR", "未知") {
        override fun matches(text: String): Boolean {
            return false
        }
    }

    internal object Serializer : KSerializer<SubtitleLanguage> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): SubtitleLanguage {
            return tryParse(String.serializer().deserialize(decoder)) ?: ParseError
        }

        override fun serialize(encoder: Encoder, value: SubtitleLanguage) {
            return String.serializer().serialize(encoder, value.id)
        }
    }

    companion object {
        val matchableEntries by lazy {
            listOf(
                ChineseSimplified,
                ChineseTraditional,
                ChineseCantonese,
                Japanese,
                English,
            )
        }

        fun tryParse(value: String): SubtitleLanguage? {
            for (entry in matchableEntries) {
                if (entry.id == value || entry.matches(value)) {
                    return entry
                }
            }
            return null
        }
    }
}