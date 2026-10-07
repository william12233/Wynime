package com.wynime.app.domain.media.selector

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.datasources.api.Media

interface MediaSelectorEvents {
    val onSelect: Flow<SelectEvent>

    val onBeforeSelect: Flow<SelectEvent>

    val onChangePreference: Flow<MediaPreference>

    val onPreferWebSource: Flow<PreferWebSourceEvent>

}

data class SelectEvent(
    val media: Media?,
    val subtitleLanguageId: String?,
    val previousMedia: Media?,
)

data class PreferWebSourceEvent(
    val subjectId: Int,
    val mediaSourceId: String
)

class MutableMediaSelectorEvents(
    replay: Int = 0,
    extraBufferCapacity: Int = 1,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) : MediaSelectorEvents {
    override val onSelect: MutableSharedFlow<SelectEvent> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
    override val onBeforeSelect: MutableSharedFlow<SelectEvent> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
    override val onChangePreference: MutableSharedFlow<MediaPreference> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
    override val onPreferWebSource: MutableSharedFlow<PreferWebSourceEvent> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
}
