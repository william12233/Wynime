package com.wynime.app.ui.subject.collection.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.app.ui.lang.*
import com.wynime.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.*

@Composable
fun EditCollectionTypeDropDown(
    state: EditableSubjectCollectionTypeState,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    EditCollectionTypeDropDown(
        currentType = presentation.selfCollectionType,
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        onClick = {
            scope.launch {
                val error = state.setSelfCollectionType(it.type)
                if (error != null) {
                    toaster.showLoadError(error)
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun EditCollectionTypeDropDown(
    currentType: UnifiedCollectionType?,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onClick: (action: SubjectCollectionAction) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<SubjectCollectionAction> = SubjectCollectionActionsForEdit,
    showDelete: Boolean = currentType != UnifiedCollectionType.NOT_COLLECTED,
) {
    var showConfirmDeleteDialog by rememberSaveable { mutableStateOf(false) }
    DropdownMenu(
        expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 4.dp),
        modifier = modifier,
    ) {
        for (action in actions) {
            if (!showDelete && action == SubjectCollectionActions.DeleteCollection) continue

            val color = action.colorForCurrent(currentType)
            DropdownMenuItem(
                text = {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        action.title()
                    }
                },
                leadingIcon = {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        action.icon()
                    }
                },
                onClick = {
                    if (action == SubjectCollectionActions.DeleteCollection) {
                        showConfirmDeleteDialog = true
                    } else {
                        onClick(action)
                        onDismissRequest()
                    }
                },
            )
        }

        if (showConfirmDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDeleteDialog = false },
                title = { Text(stringResource(Lang.subject_collection_delete_confirm_title)) },
                text = { Text(stringResource(Lang.subject_collection_delete_confirm_message)) },
                icon = { SubjectCollectionActions.DeleteCollection.icon() },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClick(SubjectCollectionActions.DeleteCollection)
                            onDismissRequest()
                            showConfirmDeleteDialog = false
                        },
                    ) {
                        Text(
                            stringResource(Lang.subject_collection_delete_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirmDeleteDialog = false },
                    ) {
                        Text(stringResource(Lang.subject_collection_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun SubjectCollectionAction.colorForCurrent(
    currentType: UnifiedCollectionType?
) = if (currentType == type) {
    MaterialTheme.colorScheme.primary
} else {
    LocalContentColor.current
}
