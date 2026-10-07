package com.wynime.app.domain.media.selector.testFramework

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorEvents
import com.wynime.app.domain.media.selector.PreferWebSourceEvent
import com.wynime.app.domain.media.selector.SelectEvent
import com.wynime.datasources.api.Media
import com.wynime.utils.coroutines.cancellableCoroutineScope
import kotlin.reflect.KClass
import kotlin.test.assertEquals

sealed class RecordedMediaSelectorEvent {
    abstract val index: Int

    abstract val selectedAtEmit: Media?

    data class OnBeforeSelect(
        override val index: Int,
        val event: SelectEvent,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()

    data class OnSelect(
        override val index: Int,
        val event: SelectEvent,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()

    data class OnChangePreference(
        override val index: Int,
        val preference: MediaPreference,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()

    data class OnPreferWebSource(
        override val index: Int,
        val event: PreferWebSourceEvent,
        override val selectedAtEmit: Media?,
    ) : RecordedMediaSelectorEvent()
}

class CollectedMediaSelectorEvents {
    private val lock = Mutex()
    private val mutableRecords: MutableList<RecordedMediaSelectorEvent> = mutableListOf()

    val records: List<RecordedMediaSelectorEvent> get() = mutableRecords.toList()

    internal suspend fun add(create: (index: Int) -> RecordedMediaSelectorEvent) {
        lock.withLock {
            mutableRecords.add(create(mutableRecords.size))
        }
    }

    val onBeforeSelect: List<RecordedMediaSelectorEvent.OnBeforeSelect>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnBeforeSelect>()
    val onSelect: List<RecordedMediaSelectorEvent.OnSelect>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnSelect>()
    val onChangePreference: List<RecordedMediaSelectorEvent.OnChangePreference>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnChangePreference>()
    val onPreferWebSource: List<RecordedMediaSelectorEvent.OnPreferWebSource>
        get() = records.filterIsInstance<RecordedMediaSelectorEvent.OnPreferWebSource>()

    fun assertOrder(vararg expected: KClass<out RecordedMediaSelectorEvent>) {
        val actual = records
        assertEquals(expected.toList(), actual.map { it::class }, "Actual records: $actual")
    }

    fun expectNoEvents() {
        assertEquals(emptyList(), records)
    }
}

suspend fun MediaSelector.collectEvents(
    block: suspend () -> Unit,
): CollectedMediaSelectorEvents {
    val collected = CollectedMediaSelectorEvents()
    cancellableCoroutineScope {
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onBeforeSelect.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnBeforeSelect(index, it, selected.value) }
            }
        }
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onSelect.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnSelect(index, it, selected.value) }
            }
        }
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onChangePreference.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnChangePreference(index, it, selected.value) }
            }
        }
        launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.onPreferWebSource.collect {
                collected.add { index -> RecordedMediaSelectorEvent.OnPreferWebSource(index, it, selected.value) }
            }
        }
        try {
            block()
            yield()
        } finally {
            cancelScope()
        }
    }
    return collected
}
