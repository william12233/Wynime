package com.wynime.app.domain.media.selector

import kotlinx.coroutines.flow.debounce
import com.wynime.app.data.models.preference.MediaPreference

inline val MediaSelector.eventHandling get() = MediaSelectorEventHandlers(this)

class MediaSelectorEventHandlers(
    private val mediaSelector: MediaSelector,
) {

    suspend fun savePreferenceOnSelect(
        save: suspend (MediaPreference) -> Unit,
    ) {
        mediaSelector.events.onChangePreference.debounce(1000).collect {
            save(it)
        }
    }

    suspend fun preferWebMediaSource(
        prefer: suspend (PreferWebSourceEvent) -> Unit,
    ) {
        mediaSelector.events.onPreferWebSource.collect {
            prefer(it)
        }
    }
}
