package com.wynime.app.ui.settings.tabs.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import com.wynime.app.data.models.preference.MediaCacheSettings
import com.wynime.app.platform.PermissionManager
import com.wynime.app.ui.foundation.getClipEntryText
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_copied_to_clipboard
import com.wynime.app.ui.lang.settings_media_source_cancel
import com.wynime.app.ui.lang.settings_media_source_delete_confirm
import com.wynime.app.ui.lang.settings_storage_backup_op_backup_description
import com.wynime.app.ui.lang.settings_storage_backup_op_backup_error
import com.wynime.app.ui.lang.settings_storage_backup_op_backup_title
import com.wynime.app.ui.lang.settings_storage_backup_op_restore
import com.wynime.app.ui.lang.settings_storage_backup_op_restore_description
import com.wynime.app.ui.lang.settings_storage_backup_op_restore_error
import com.wynime.app.ui.lang.settings_storage_backup_op_restore_succees
import com.wynime.app.ui.lang.settings_storage_backup_op_restore_warning
import com.wynime.app.ui.lang.settings_storage_backup_title
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Stable
class CacheDirectoryGroupState(
    val mediaCacheSettingsState: SettingsState<MediaCacheSettings>,
    val permissionManager: PermissionManager,
    val onGetBackupData: suspend () -> String,
    val onRestoreSettings: suspend (String) -> Boolean,
)

@Composable
fun SettingsScope.BackupSettings(state: CacheDirectoryGroupState) {
    var showRestoreDialog by remember { mutableStateOf(false) }

    val scope = rememberAsyncHandler()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current

    Group({ Text(stringResource(Lang.settings_storage_backup_title)) }) {
        val backupErrorText = stringResource(Lang.settings_storage_backup_op_backup_error)

        TextItem(
            onClick = {
                scope.launch {
                    val data = state.onGetBackupData()
                    clipboard.setClipEntryText(data)
                    toaster.toast(getString(Lang.settings_copied_to_clipboard))
                }
            },
            title = { Text(stringResource(Lang.settings_storage_backup_op_backup_title)) },
            description = { Text(stringResource(Lang.settings_storage_backup_op_backup_description)) },
        )
        TextItem(
            onClick = { showRestoreDialog = true },
            title = { Text(stringResource(Lang.settings_storage_backup_op_restore)) },
            description = { Text(stringResource(Lang.settings_storage_backup_op_restore_description)) },
        )
    }

    if (showRestoreDialog) {
        val restoreSuccess = stringResource(Lang.settings_storage_backup_op_restore_succees)
        val restoreFailed = stringResource(Lang.settings_storage_backup_op_restore_error)

        AlertDialog(
            { showRestoreDialog = false },
            icon = { Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Lang.settings_storage_backup_op_restore)) },
            text = { Text(stringResource(Lang.settings_storage_backup_op_restore_warning)) },
            confirmButton = {
                TextButton(
                    {
                        scope.launch {
                            val clipboardText = clipboard.getClipEntryText()
                                ?.takeIf { it.isNotBlank() && it.isNotEmpty() }
                            val result = clipboardText?.let { state.onRestoreSettings(it) } == true

                            toaster.toast(if (result) restoreSuccess else restoreFailed)
                            showRestoreDialog = false
                        }
                    },
                ) {
                    Text(stringResource(Lang.settings_media_source_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showRestoreDialog = false }) {
                    Text(stringResource(Lang.settings_media_source_cancel))
                }
            },
        )
    }
}

@Composable
expect fun SettingsScope.CacheDirectoryGroup(
    state: CacheDirectoryGroupState,
)
