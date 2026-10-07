package com.wynime.app.domain.media.fetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.datasources.api.source.MediaSourceTier
import com.wynime.utils.coroutines.flows.flowOfEmptyList
import com.wynime.utils.platform.collections.tupleOf

class MediaSourceResultsFilterer(
    results: Flow<List<MediaSourceFetchResult>>,
    settings: Flow<MediaSelectorSettings>,
    flowScope: CoroutineScope,
) {

    val filteredSourceResults: Flow<List<MediaSourceFetchResult>> = combine(results, settings) { r, s ->
        tupleOf(r, s)
    }.flatMapLatest { (results, settings) ->
        if (results.isEmpty()) {
            return@flatMapLatest flowOfEmptyList()
        }
        combine(
            results.map { result ->
                result.state.map { it.isDisabled }.distinctUntilChanged()
            },
        ) { array ->
            fun isDisabled(result: MediaSourceFetchResult): Boolean {
                return array[results.indexOf(result)]
            }

            val candidates = results.filterTo(mutableListOf()) {
                if (!settings.showDisabled && isDisabled(it)) return@filterTo false
                true
            }

            candidates.sortedWith(
                compareBy<MediaSourceFetchResult> { result ->
                    if (isDisabled(result)) {
                        MediaSourceTier(UInt.MAX_VALUE)
                    } else {
                        result.sourceInfo.tier
                    }
                }.then(compareBy { it.mediaSourceId }),
            )
        }
    }.distinctUntilChanged()
}
