package com.wynime.app.ui.subject.collection.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.tools.MonoTasker
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.lang.*
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.*
import kotlin.coroutines.cancellation.CancellationException

interface SubjectCollectionTypeEditActions {

    suspend fun setSelfCollectionType(new: UnifiedCollectionType): LoadError?

    val shouldOfferMarkAllWatched: Boolean

    fun setAllEpisodesWatched()

    suspend fun setAllEpisodesWatchedAwait(): LoadError?
    fun dismissSetAllEpisodesDoneDialog()

    companion object Noop : SubjectCollectionTypeEditActions {
        override suspend fun setSelfCollectionType(new: UnifiedCollectionType): LoadError? = null
        override val shouldOfferMarkAllWatched: Boolean get() = false
        override fun setAllEpisodesWatched() {}
        override suspend fun setAllEpisodesWatchedAwait(): LoadError? = null
        override fun dismissSetAllEpisodesDoneDialog() {}
    }
}

@Stable
class EditableSubjectCollectionTypeState(
    selfCollectionTypeFlow: Flow<UnifiedCollectionType>,
    private val hasAnyUnwatched: suspend () -> Boolean,
    private val onSetSelfCollectionType: suspend (UnifiedCollectionType) -> Unit,
    private val onSetAllEpisodesWatched: suspend () -> Unit,
    private val backgroundScope: CoroutineScope,
) : SubjectCollectionTypeEditActions {
    data class Presentation(
        val selfCollectionType: UnifiedCollectionType,
        val isSetSelfCollectionTypeWorking: Boolean,
        val isSetAllEpisodesWatchedWorking: Boolean,
        val showSetAllEpisodesDoneDialog: Boolean,
        val isPlaceholder: Boolean = false,
    ) {
        companion object {
            val Placeholder = Presentation(
                UnifiedCollectionType.WISH,
                false,
                false,
                false,
                isPlaceholder = true,
            )
        }
    }

    private val showSetAllEpisodesDoneDialogFlow = MutableStateFlow(false)
    override val shouldOfferMarkAllWatched: Boolean get() = showSetAllEpisodesDoneDialogFlow.value

    private val setSelfCollectionTypeTasker = MonoTasker(backgroundScope)

    private val setAllEpisodesWatchedTasker = MonoTasker(backgroundScope)

    val presentationFlow: StateFlow<Presentation> =
        combine(
            selfCollectionTypeFlow,
            setSelfCollectionTypeTasker.isRunning,
            showSetAllEpisodesDoneDialogFlow,
            setAllEpisodesWatchedTasker.isRunning,
        ) { type, setSelfCollectionTypeTaskerWorking, showSetAllEpisodesDoneDialog, setAllEpisodesWatchedWorking ->
            Presentation(
                selfCollectionType = type,
                isSetSelfCollectionTypeWorking = setSelfCollectionTypeTaskerWorking,
                isSetAllEpisodesWatchedWorking = setAllEpisodesWatchedWorking,
                showSetAllEpisodesDoneDialog = showSetAllEpisodesDoneDialog,
            )
        }.stateIn(
            backgroundScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = Presentation.Placeholder,
        )

    override suspend fun setSelfCollectionType(new: UnifiedCollectionType): LoadError? {
        return setSelfCollectionTypeTasker.async {
            try {
                onSetSelfCollectionType(new)
                if (new == UnifiedCollectionType.DONE && hasAnyUnwatched()) {
                    showSetAllEpisodesDoneDialogFlow.value = true
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LoadError.fromException(e)
            }
        }.await()
    }

    override fun setAllEpisodesWatched() {
        backgroundScope.launch { setAllEpisodesWatchedAwait() }
    }

    override suspend fun setAllEpisodesWatchedAwait(): LoadError? = setAllEpisodesWatchedTasker.async {
        LoadError.runAndWrapOrThrowCancellation { onSetAllEpisodesWatched() }
    }.await()

    override fun dismissSetAllEpisodesDoneDialog() {
        showSetAllEpisodesDoneDialogFlow.value = false
    }
}

@Composable
fun EditableSubjectCollectionTypeButton(
    state: EditableSubjectCollectionTypeState,
    modifier: Modifier = Modifier,
) {
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    EditableSubjectCollectionTypeButton(presentation, state, modifier)
}

@Composable
fun EditableSubjectCollectionTypeButton(
    presentation: EditableSubjectCollectionTypeState.Presentation,
    actions: SubjectCollectionTypeEditActions,
    modifier: Modifier = Modifier,
) {

    EditableSubjectCollectionTypeDialogsHost(presentation, actions)

    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    SubjectCollectionTypeButton(
        presentation.selfCollectionType,
        onEdit = {
            scope.launch {
                val error = actions.setSelfCollectionType(it)
                error?.let(toaster::showLoadError)
            }
        },
        modifier = modifier.placeholder(presentation.isPlaceholder),
        enabled = !presentation.isSetSelfCollectionTypeWorking,
    )
}

@Composable
fun EditableSubjectCollectionTypeDialogsHost(
    state: EditableSubjectCollectionTypeState,
) {
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    EditableSubjectCollectionTypeDialogsHost(presentation, state)
}

@Composable
fun EditableSubjectCollectionTypeDialogsHost(
    presentation: EditableSubjectCollectionTypeState.Presentation,
    actions: SubjectCollectionTypeEditActions,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    if (presentation.showSetAllEpisodesDoneDialog) {
        SetAllEpisodeDoneDialog(
            onDismissRequest = { actions.dismissSetAllEpisodesDoneDialog() },
            isWorking = presentation.isSetAllEpisodesWatchedWorking,
            onConfirm = {
                scope.launch {
                    val error = actions.setAllEpisodesWatchedAwait()
                    if (error == null) actions.dismissSetAllEpisodesDoneDialog()
                    else toaster.showLoadError(error)
                }
            },
        )
    }
}

@Composable
private fun SetAllEpisodeDoneDialog(
    isWorking: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.TaskAlt, null) },
        text = { Text(stringResource(Lang.subject_collection_set_all_episodes_watched)) },
        confirmButton = {
            TextButton(onConfirm, enabled = !isWorking) { Text(stringResource(Lang.subject_collection_set)) }

            if (isWorking) {
                CircularProgressIndicator(Modifier.padding(start = 8.dp).size(24.dp))
            }
        },
        dismissButton = { TextButton(onDismissRequest) { Text(stringResource(Lang.subject_collection_ignore)) } },
        modifier = modifier,
    )
}

@TestOnly
@Composable
fun rememberTestEditableSubjectCollectionTypeState(type: UnifiedCollectionType = UnifiedCollectionType.WISH): EditableSubjectCollectionTypeState {
    val backgroundScope = rememberCoroutineScope()
    val selfCollectionType = remember {
        MutableStateFlow(type)
    }
    return remember {
        createTestEditableSubjectCollectionTypeState(selfCollectionType, backgroundScope)
    }
}

@TestOnly
fun createTestEditableSubjectCollectionTypeState(
    selfCollectionType: MutableStateFlow<UnifiedCollectionType>,
    backgroundScope: CoroutineScope
) = EditableSubjectCollectionTypeState(
    selfCollectionType,
    hasAnyUnwatched = { false },
    onSetSelfCollectionType = {
        selfCollectionType.value = it
    },
    onSetAllEpisodesWatched = { },
    backgroundScope,
)
