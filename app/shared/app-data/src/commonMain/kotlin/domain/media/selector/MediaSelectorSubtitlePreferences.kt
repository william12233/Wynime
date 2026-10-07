package com.wynime.app.domain.media.selector

import com.wynime.app.domain.media.selector.SubtitleKindPreference.HIDE
import com.wynime.app.domain.media.selector.SubtitleKindPreference.NORMAL
import com.wynime.datasources.api.SubtitleKind
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.collections.EnumMap
import com.wynime.utils.platform.collections.ImmutableEnumMap
import com.wynime.utils.platform.currentPlatform
import kotlin.jvm.JvmInline

@JvmInline
value class MediaSelectorSubtitlePreferences(
    val values: EnumMap<SubtitleKind, SubtitleKindPreference>
) {
    operator fun get(kind: SubtitleKind): SubtitleKindPreference = values[kind]

    fun copy(
        values: EnumMap<SubtitleKind, SubtitleKindPreference> = this.values,
    ): MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences(values)

    companion object {

        val AllNormal = MediaSelectorSubtitlePreferences(
            ImmutableEnumMap { NORMAL },
        )

        val CurrentPlatform by lazy {
            forPlatform()
        }

        fun forPlatform(platform: Platform = currentPlatform()): MediaSelectorSubtitlePreferences {

            val map = when (platform) {

                is Platform.Windows -> ImmutableEnumMap<SubtitleKind, _> {
                    when (it) {
                        SubtitleKind.EMBEDDED -> NORMAL
                        SubtitleKind.CLOSED -> NORMAL
                        SubtitleKind.EXTERNAL_PROVIDED -> NORMAL
                        SubtitleKind.EXTERNAL_DISCOVER -> HIDE
                        SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> NORMAL
                    }
                }

                is Platform.Android -> ImmutableEnumMap<SubtitleKind, _> {
                    when (it) {
                        SubtitleKind.EMBEDDED -> NORMAL
                        SubtitleKind.CLOSED -> NORMAL
                        SubtitleKind.EXTERNAL_PROVIDED -> NORMAL
                        SubtitleKind.EXTERNAL_DISCOVER -> HIDE
                        SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> NORMAL
                    }
                }

            }

            return MediaSelectorSubtitlePreferences(map)
        }
    }
}

enum class SubtitleKindPreference {

    NORMAL,

    LOW_PRIORITY,

    HIDE,
}
