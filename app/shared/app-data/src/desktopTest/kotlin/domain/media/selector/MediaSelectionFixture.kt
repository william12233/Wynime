package com.wynime.app.domain.media.selector

import kotlinx.serialization.Serializable
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.selector.trace.MediaSelectionTrace
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.source.MediaFetchRequest

@Serializable
internal class MediaSelectionFixture(
    val formatVersion: Int,
    val provenance: Provenance,
    val request: MediaFetchRequest,
    val preferredSourceId: String?,
    val durationMillis: Long,
    val sources: List<MediaSelectionTrace.Source>,
    val initial: Inputs,
    val media: List<DefaultMedia>,
    val events: List<Event>,
) {
    @Serializable
    data class Provenance(
        val capturedAt: String,
        val applicationVersion: String,
        val acquisition: String,
        val searchCacheCleared: Boolean,
        val rawSha256: String,
        val rawBytes: Int,
        val rawFrameCount: Int,
    )

    @Serializable
    data class Inputs(
        val context: MediaSelectionTrace.Context,
        val settings: MediaSelectorSettings,
        val preference: MediaPreference,
        val defaultPreference: MediaPreference,
    )

    @Serializable
    data class SourceChange(val source: Int, val state: String, val generation: Int?, val media: List<Int>)

    @Serializable
    data class Event(
        val elapsedMillis: Long,
        val sources: List<SourceChange>,
        val selectedMediaId: String?,
        val context: MediaSelectionTrace.Context? = null,
        val settings: MediaSelectorSettings? = null,
        val preference: MediaPreference? = null,
        val defaultPreference: MediaPreference? = null,
    )

    fun restore(): MediaSelectionTrace {
        require(formatVersion == 1)
        require(events.isNotEmpty() && events.size <= provenance.rawFrameCount)
        require(events.zipWithNext().all { (a, b) -> a.elapsedMillis <= b.elapsedMillis })
        require(events.last().elapsedMillis <= durationMillis)
        val states = sources.map { MediaSelectionTrace.SourceUpdate(it.id, "Idle", null, emptyList()) }.toMutableList()
        var inputs = initial
        val frames = events.map { event ->
            inputs = Inputs(
                event.context ?: inputs.context, event.settings ?: inputs.settings,
                event.preference ?: inputs.preference, event.defaultPreference ?: inputs.defaultPreference,
            )
            event.sources.forEach { change ->
                states[change.source] = MediaSelectionTrace.SourceUpdate(
                    sources[change.source].id, change.state, change.generation, change.media.map { media[it] },
                )
            }
            MediaSelectionTrace.Frame(
                event.elapsedMillis, states.toList(), inputs.context, inputs.settings,
                inputs.preference, inputs.defaultPreference, event.selectedMediaId,
            )
        }
        return MediaSelectionTrace(
            capturedAt = provenance.capturedAt,
            applicationVersion = provenance.applicationVersion,
            acquisition = provenance.acquisition,
            durationMillis = durationMillis,
            searchCacheCleared = provenance.searchCacheCleared,
            request = request,
            preferredSourceId = preferredSourceId,
            sources = sources,
            frames = frames,
        )
    }
}
