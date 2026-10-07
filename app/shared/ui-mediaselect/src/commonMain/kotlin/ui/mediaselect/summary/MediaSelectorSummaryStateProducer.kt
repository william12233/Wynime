package com.wynime.app.ui.mediaselect.summary

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformLatest
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.DroppedFileMedia
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.domain.media.fetch.MediaSourceInfoWithId
import com.wynime.app.domain.media.selector.MatchMetadata
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.UnsafeOriginalMediaAccess
import com.wynime.app.domain.media.selector.isPerfectMatch
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.media_selector_summary_dropped_file
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.platform.collections.tupleOf
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

class MediaSelectorSummaryStateProducer(
    selectedMaybeExcludedMediaFlow: Flow<MaybeExcludedMedia?>,
    mediaSourceResultsFlow: Flow<List<MediaSourceFetchResult>>,
    mediaSelectorSettingsFlow: Flow<MediaSelectorSettings>,
    mediaSources: Flow<List<MediaSourceInfoWithId>>,

    droppedFileSourceName: suspend () -> String = { getString(Lang.media_selector_summary_dropped_file) },
) {
    private val sourceSummariesFlow = combine(mediaSourceResultsFlow, mediaSources) { results, sources ->
        tupleOf(results, sources)
    }.flatMapLatest { (results, sourcesSorted) ->
        combine(
            results.map { result ->
                result.state.map { it is MediaSourceFetchState.Completed }.distinctUntilChanged()
            },
        ) { states ->
            results
                .asSequence()
                .filter { !it.sourceInfo.isSpecial }
                .filter { it.kind == MediaSourceKind.WEB }
                .filter { states[results.indexOf(it)] }
                .sortedWith(
                    compareBy { source ->
                        sourcesSorted.indexOfFirst { it.instanceId == source.instanceId }
                    },
                )
                .map {
                    it.sourceInfo.toSummary()
                }
                .toList()
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    val flow = combine(
        sourceSummariesFlow,
        mediaSelectorSettingsFlow,
        mediaSources,
    ) { sourceSummaries, mediaSelectorSettings, mediaSourceInstancesSorted ->
        tupleOf(sourceSummaries, mediaSelectorSettings, mediaSourceInstancesSorted)
    }.transformLatest { (sourceSummaries, mediaSelectorSettings, mediaSourceInstances) ->
        emitAll(
            selectedMaybeExcludedMediaFlow.map { selected ->
                when {
                    selected != null -> {
                        MediaSelectorSummary.Selected(
                            mediaSourceInstances.find { it.mediaSourceId == selected.original.mediaSourceId }
                                ?.info
                                ?.toSummary()
                                ?: MediaSelectorSourceSummary(
                                    sourceName = if (DroppedFileMedia.isDroppedFile(selected.original)) {
                                        droppedFileSourceName()
                                    } else {
                                        selected.original.mediaSourceId
                                    },
                                    sourceIconUrl = "",
                                ),
                            selected.original.originalTitle,
                            isPerfectMatch = selected.isPerfectMatch(),
                        )
                    }

                    mediaSelectorSettings.preferKind == MediaSourceKind.WEB -> {
                        MediaSelectorSummary.AutoSelecting(
                            sources = sourceSummaries,
                            estimate = if (mediaSelectorSettings.fastSelectWebKind) mediaSelectorSettings.fastSelectWebLowTierToleranceDuration
                            else 10.seconds,
                        )
                    }

                    else -> {
                        MediaSelectorSummary.RequiresManualSelection(
                            sources = sourceSummaries,
                        )
                    }
                }
            },
        )
    }.distinctUntilChanged()
}

@OptIn(UnsafeOriginalMediaAccess::class)
val MediaSelector.selectedMaybeExcludedMediaFlow: Flow<MaybeExcludedMedia?>
    get() = this.selected.mapLatest { selected ->
        if (selected == null) {
            null
        } else {
            filteredCandidates.first()
                .firstOrNull { it.original === selected }
                ?: MaybeExcludedMedia.Included(
                    selected,
                    MatchMetadata(
                        MatchMetadata.SubjectMatchKind.FUZZY,
                        MatchMetadata.EpisodeMatchKind.NONE,
                        similarity = 0,
                    ),
                )
        }
    }

private fun MediaSourceInfo.toSummary() =
    MediaSelectorSourceSummary(
        displayName,
        iconUrl ?: "",
    )
