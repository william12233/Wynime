package com.wynime.app.ui.subject.details.state

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.subject.details.SubjectDetailsLoadState
import com.wynime.utils.platform.annotations.TestOnly

@Stable
class SubjectDetailsStateLoader(
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory,
    backgroundScope: CoroutineScope,
) {

    private data class Request(
        val subjectId: Int,
        val placeholder: SubjectInfo?,
        val attempt: Int,
    )

    private val request = MutableStateFlow<Request?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SubjectDetailsLoadState> = request
        .flatMapLatest { req ->
            if (req == null) return@flatMapLatest flowOf(Idle)
            flow<SubjectDetailsLoadState> {
                emit(SubjectDetailsLoadState.Placeholder(req.subjectId, req.placeholder))
                emitAll(
                    subjectDetailsStateFactory.create(req.subjectId, req.placeholder)
                        .map { SubjectDetailsLoadState.Ok(it.subjectId, it) },
                )
            }.catch { e ->
                emit(SubjectDetailsLoadState.Err(req.subjectId, req.placeholder, LoadError.fromException(e)))
            }
        }
        .stateIn(backgroundScope, SharingStarted.Eagerly, Idle)

    fun load(
        subjectId: Int,
        placeholder: SubjectInfo? = null,
        force: Boolean = false,
    ) {
        if (!force && request.value?.subjectId == subjectId && state.value !is SubjectDetailsLoadState.Err) {
            return
        }
        request.value = Request(subjectId, placeholder, nextAttempt())
    }

    fun retry() {
        val err = state.value as? SubjectDetailsLoadState.Err ?: return
        load(err.subjectId, err.placeholder, force = true)
    }

    fun clear() {
        request.value = null
    }

    private companion object {

        private val Idle = SubjectDetailsLoadState.Placeholder(subjectId = 0)
    }

    private fun nextAttempt(): Int = (request.value?.attempt ?: 0) + 1
}

@TestOnly
fun createTestSubjectDetailsLoader(
    backgroundScope: CoroutineScope,
    subjectDetailsStateFactory: SubjectDetailsStateFactory = TestSubjectDetailsStateFactory(),
): SubjectDetailsStateLoader {
    return SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
}
