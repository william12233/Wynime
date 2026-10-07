package com.wynime.app.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import com.wynime.app.platform.LocalContext
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_update_manual_install_cancel
import com.wynime.app.ui.lang.settings_update_manual_install_open_failed
import com.wynime.app.ui.lang.settings_update_manual_install_package_not_found
import com.wynime.app.ui.lang.settings_update_manual_install_title
import com.wynime.app.ui.lang.settings_update_manual_install_view_package
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
fun FailedToInstallDialog(
    message: String,
    onDismissRequest: () -> Unit,
    state: AppUpdateState,
) {
    FailedToInstallDialog(
        message,
        onDismissRequest,
        file = (state as? AppUpdateState.Downloaded)?.file,
    )
}

@Composable
fun FailedToInstallDialog(
    message: String,
    onDismissRequest: () -> Unit,
    file: SystemPath?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    AlertDialog(
        onDismissRequest,
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (file == null) {
                            toaster.toast(getString(Lang.settings_update_manual_install_package_not_found))
                            return@launch
                        }
                        val success =
                            KoinPlatform.getKoin().get<UpdateInstaller>().openForManualInstallation(file, context)

                        if (!success) {
                            toaster.toast(
                                getString(
                                    Lang.settings_update_manual_install_open_failed,
                                    file.absolutePath,
                                ),
                            )
                        }
                    }
                },
            ) { Text(stringResource(Lang.settings_update_manual_install_view_package)) }
        },
        dismissButton = {
            TextButton(
                onDismissRequest,
                modifier = Modifier.testTag(FailedToInstallDialogTestTags.DISMISS_BUTTON),
            ) { Text(stringResource(Lang.settings_update_manual_install_cancel)) }
        },
        title = { Text(stringResource(Lang.settings_update_manual_install_title)) },
        text = { Text(message) },
    )
}

object FailedToInstallDialogTestTags {
    const val DISMISS_BUTTON = "failed_to_install_dialog_dismiss"
}
