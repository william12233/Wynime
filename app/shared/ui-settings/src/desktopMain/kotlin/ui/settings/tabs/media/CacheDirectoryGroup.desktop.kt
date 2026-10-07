package com.wynime.app.ui.settings.tabs.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import com.wynime.app.platform.LocalDesktopContext
import com.wynime.app.platform.files
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_storage_media_cache_location
import com.wynime.app.ui.lang.settings_storage_media_cache_location_description
import com.wynime.app.ui.lang.settings_storage_choose_directory
import com.wynime.app.ui.lang.settings_storage_directory_create_failed
import com.wynime.app.ui.lang.settings_storage_directory_not_exist
import com.wynime.app.ui.lang.settings_storage_open_media_cache_directory
import com.wynime.app.ui.lang.settings_storage_open_directory_chooser
import com.wynime.app.ui.lang.settings_storage_path_is_invalid
import com.wynime.app.ui.lang.settings_storage_title
import com.wynime.app.ui.settings.framework.components.RowButtonItem
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.TextFieldItem
import com.wynime.utils.io.absolutePath
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File

@Composable
actual fun SettingsScope.CacheDirectoryGroup(state: CacheDirectoryGroupState) {
    Group({ Text(stringResource(Lang.settings_storage_title)) }) {
        val mediaCacheSettings by state.mediaCacheSettingsState

        val context = LocalDesktopContext.current

        val defaultSaveDir = remember { context.files.defaultMediaCacheBaseDir.absolutePath }
        val currentSaveDir: String by remember {
            derivedStateOf {
                mediaCacheSettings.saveDir ?: defaultSaveDir
            }
        }

        val toaster = LocalToaster.current

        val directoryNotExistMessage = stringResource(Lang.settings_storage_directory_not_exist)
        val pathIsInvalidMessage = stringResource(Lang.settings_storage_path_is_invalid)
        val directoryCreateFailed = stringResource(Lang.settings_storage_directory_create_failed)

        TextFieldItem(
            currentSaveDir,
            title = { Text(stringResource(Lang.settings_storage_media_cache_location)) },
            onValueChangeCompleted = {
                val dir = try {
                    File(it).canonicalFile
                } catch (e: Exception) {
                    toaster.toast("$pathIsInvalidMessage: ${e.message}")
                    return@TextFieldItem
                }

                if (!dir.exists() && !dir.mkdirs()) {
                    toaster.toast("$directoryCreateFailed: ${dir.path}")
                    return@TextFieldItem
                }

                if (!dir.isDirectory) {
                    toaster.toast(pathIsInvalidMessage)
                    return@TextFieldItem
                }

                state.mediaCacheSettingsState.update(
                    mediaCacheSettings.copy(saveDir = dir.path),
                )
            },
            textFieldDescription = {
                Text(stringResource(Lang.settings_storage_media_cache_location_description))
            },
            extra = { textFieldValue ->
                val directoryPicker = rememberDirectoryPickerLauncher(
                    directory = PlatformFile(currentSaveDir),
                    dialogSettings = FileKitDialogSettings(
                        title = stringResource(Lang.settings_storage_choose_directory),
                    ),
                ) {
                    it?.let {
                        textFieldValue.value = it.file.absolutePath
                    }
                }
                OutlinedButton({ directoryPicker.launch() }) {
                    Text(stringResource(Lang.settings_storage_open_directory_chooser))
                }
            },
        )
        RowButtonItem(
            title = { Text(stringResource(Lang.settings_storage_open_media_cache_directory)) },
            icon = { Icon(Icons.Rounded.ArrowOutward, null) },
            onClick = {
                val file = File(mediaCacheSettings.saveDir ?: defaultSaveDir)
                if (file.exists()) {
                    Desktop.getDesktop().open(file)
                } else {
                    toaster.toast(directoryNotExistMessage)
                }
            },
        )
    }
}
