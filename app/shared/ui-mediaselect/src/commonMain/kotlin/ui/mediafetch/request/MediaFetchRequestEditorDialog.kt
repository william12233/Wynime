package com.wynime.app.ui.mediafetch.request

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wynime.app.ui.foundation.saveable.mutableStateSaver
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.mediafetch_request_editor_continue_editing
import com.wynime.app.ui.lang.mediafetch_request_editor_discard
import com.wynime.app.ui.lang.mediafetch_request_editor_discard_confirmation
import com.wynime.app.ui.lang.mediafetch_request_editor_invalid_request
import com.wynime.app.ui.lang.mediafetch_request_editor_save_and_refresh
import com.wynime.app.ui.lang.mediafetch_request_editor_title
import com.wynime.app.ui.lang.settings_cancel
import com.wynime.datasources.api.source.MediaFetchRequest
import org.jetbrains.compose.resources.stringResource

@Composable
fun MediaFetchRequestEditorDialog(
    fetchRequest: MediaFetchRequest,
    onDismissRequest: () -> Unit,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
) {
    var editingRequest by rememberSaveable(
        fetchRequest,
        saver = mutableStateSaver(EditingMediaFetchRequest.Saver),
    ) {
        mutableStateOf(fetchRequest.toEditingMediaFetchRequest())
    }
    var showConfirmDiscard by rememberSaveable { mutableStateOf(false) }
    val onDismissRequestWrapped = {
        val hasChange = editingRequest != fetchRequest.toEditingMediaFetchRequest()
        if (hasChange) {
            showConfirmDiscard = true
        } else {
            onDismissRequest()
        }
    }

    val toaster = LocalToaster.current
    val invalidRequestText = stringResource(Lang.mediafetch_request_editor_invalid_request)
    val saveAndRefreshText = stringResource(Lang.mediafetch_request_editor_save_and_refresh)
    val cancelText = stringResource(Lang.settings_cancel)
    val editRequestTitle = stringResource(Lang.mediafetch_request_editor_title)
    val discardText = stringResource(Lang.mediafetch_request_editor_discard)
    val continueEditingText = stringResource(Lang.mediafetch_request_editor_continue_editing)
    val discardConfirmationText = stringResource(Lang.mediafetch_request_editor_discard_confirmation)

    AlertDialog(
        onDismissRequestWrapped,
        confirmButton = {
            TextButton(
                {
                    editingRequest.toMediaFetchRequestOrNull(fetchRequest.episodes)?.let {
                        onDismissRequestWrapped()
                        onFetchRequestChange(it)
                    } ?: toaster.toast(invalidRequestText)
                },
                enabled = editingRequest.toMediaFetchRequestOrNull(fetchRequest.episodes) != null,
            ) {
                Text(saveAndRefreshText)
            }
        },
        dismissButton = {
            TextButton(onDismissRequestWrapped) {
                Text(cancelText)
            }
        },
        title = {
            Text(editRequestTitle)
        },
        text = {
            MediaFetchRequestEditor(
                editingRequest,
                { editingRequest = it },
                Modifier.fillMaxWidth(),
            )
        },
    )

    if (showConfirmDiscard) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDiscard = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDiscard = false
                        onDismissRequest()
                    },
                ) {
                    Text(discardText, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDiscard = false
                    },
                ) {
                    Text(continueEditingText)
                }
            },
            icon = {
                Icon(
                    Icons.Rounded.Delete, null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(discardConfirmationText)
            },
        )
    }
}
