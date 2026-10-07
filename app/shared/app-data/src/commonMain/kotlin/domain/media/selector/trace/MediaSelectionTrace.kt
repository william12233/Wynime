package com.wynime.app.domain.media.selector.trace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.media.selector.MediaSelectorSubtitlePreferences
import com.wynime.app.domain.media.selector.SubtitleKindPreference
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceTier
import com.wynime.utils.platform.collections.ImmutableEnumMap

@Serializable
class MediaSelectionTrace(
    val formatVersion: Int = 1,
    val capturedAt: String,
    val applicationVersion: String,
    val acquisition: String = "desktop-playback",
    val durationMillis: Long,
    val searchCacheCleared: Boolean = false,
    val request: MediaFetchRequest,
    val preferredSourceId: String?,
    val sources: List<Source>,
    val frames: List<Frame>,
) {
    @Serializable
    data class Source(val id: String, val name: String, val kind: MediaSourceKind)

    @Serializable
    data class Frame(
        val elapsedMillis: Long,

        val sources: List<SourceUpdate>,
        val context: Context,
        val settings: MediaSelectorSettings,
        val preference: MediaPreference,
        val defaultPreference: MediaPreference,
        val selectedMediaId: String?,
    )

    @Serializable
    data class SourceUpdate(
        val id: String,
        val state: String,
        val generation: Int?,
        val results: List<DefaultMedia>,
    )

    @Serializable
    data class Subject(val id: Int, val name: String, val nameCn: String, val aliases: List<String>) {
        fun restore(): SubjectInfo = SubjectInfo.Empty.copy(subjectId = id, name = name, nameCn = nameCn, aliases = aliases)
    }

    @Serializable
    data class Series(val seasonSort: Int, val sequelNames: Set<String>, val otherNames: Set<String>) {
        fun restore(): SubjectSeriesInfo = SubjectSeriesInfo(seasonSort, sequelNames, otherNames)
    }

    @Serializable
    data class Context(
        val subjectFinished: Boolean?,
        val sourcePrecedence: List<String>?,
        val subtitlePreferences: Map<SubtitleKind, SubtitleKindPreference>?,
        val series: Series?,
        val subject: Subject?,
        val episode: EpisodeInfo?,
        val sourceTiers: Map<String, UInt>?,
        val channelTiers: Map<String, Map<String, UInt>>?,
    ) {
        fun restore(): MediaSelectorContext = MediaSelectorContext(
            subjectFinished, sourcePrecedence,
            subtitlePreferences?.let { preferences ->
                MediaSelectorSubtitlePreferences(ImmutableEnumMap { preferences.getValue(it) })
            },
            series?.restore(), subject?.restore(), episode,
            sourceTiers?.let { tiers ->
                MediaSelectorSourceTiers(
                    tiers.mapValues { MediaSourceTier(it.value) },
                    channelTiers.orEmpty().mapValues { (_, channels) -> channels.mapValues { MediaSourceTier(it.value) } },
                )
            },
        )

        companion object {
            fun capture(context: MediaSelectorContext, sourceIds: List<String>): Context = Context(
                context.subjectFinished, context.mediaSourcePrecedence,
                context.subtitlePreferences?.let { preferences -> SubtitleKind.entries.associateWith { preferences[it] } },
                context.subjectSeriesInfo?.let { Series(it.seasonSort, it.sequelSubjectNames, it.seriesSubjectNamesWithoutSelf) },
                context.subjectInfo?.let { Subject(it.subjectId, it.name, it.nameCn, it.aliases) },
                context.episodeInfo?.copy(desc = ""),
                context.mediaSourceTiers?.let { tiers -> sourceIds.associateWith { tiers[it].value } },
                context.mediaSourceTiers?.channelTiers?.mapValues { (_, channels) -> channels.mapValues { it.value.value } },
            )
        }
    }

    companion object {
        val json = Json { prettyPrint = true; encodeDefaults = true }
    }
}
