package com.wynime.app.ui.subject.relations

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import com.wynime.app.data.models.subject.SubjectRelationGraph
import com.wynime.app.data.repository.subject.SubjectRelationGraphRepository
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Immutable
data class SubjectRelationGraphUiState(
    val graph: SubjectRelationGraph?,
    val error: LoadError?,
) {
    companion object {
        val Loading = SubjectRelationGraphUiState(graph = null, error = null)
    }
}

class SubjectRelationGraphViewModel(
    val subjectId: Int,
) : AbstractViewModel(), KoinComponent {
    private val repository: SubjectRelationGraphRepository by inject()
    private val restarter = FlowRestarter()

    val state: StateFlow<SubjectRelationGraphUiState> = repository.subjectRelationGraphFlow(subjectId)
        .map { SubjectRelationGraphUiState(it, error = null) }
        .onStart { emit(SubjectRelationGraphUiState.Loading) }
        .catch { emit(SubjectRelationGraphUiState(graph = null, error = LoadError.fromException(it))) }
        .restartable(restarter)
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5000), SubjectRelationGraphUiState.Loading)

    fun retry() {
        restarter.restart()
    }
}
