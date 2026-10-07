package com.wynime.app.ui.settings.tabs.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.wynime.app.data.models.preference.PlayerKernelConfig
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.models.preference.parseMpvOptions
import com.wynime.app.data.models.preference.splitMpvOptionLines
import com.wynime.app.ui.foundation.effects.defaultFocus
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_player_mpv_options
import com.wynime.app.ui.lang.settings_player_mpv_options_count
import com.wynime.app.ui.lang.settings_player_mpv_options_description
import com.wynime.app.ui.lang.settings_player_mpv_options_empty
import com.wynime.app.ui.lang.settings_player_mpv_options_placeholder
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.TextFieldDialog
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun SettingsScope.PlayerGroupPlatform(
    @Suppress("UNUSED_PARAMETER") videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    playerKernelConfig: SettingsState<PlayerKernelConfig>,
) {
    HorizontalDividerItem()
    MpvOptionsItem(playerKernelConfig)
}

internal object MpvOptionsItemTestTags {
    const val ITEM = "mpv_options_item"
    const val TEXT_FIELD = "mpv_options_text_field"
}

@Composable
private fun SettingsScope.MpvOptionsItem(playerKernelConfig: SettingsState<PlayerKernelConfig>) {
    val config by playerKernelConfig
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val optionCount = remember(config.mpvOptions) { parseMpvOptions(config.mpvOptions).size }

    Box {
        Item(
            headlineContent = { Text(stringResource(Lang.settings_player_mpv_options)) },
            Modifier.testTag(MpvOptionsItemTestTags.ITEM).clickable { showDialog = true },
            supportingContent = {
                Text(
                    if (optionCount == 0) {
                        stringResource(Lang.settings_player_mpv_options_empty)
                    } else {
                        stringResource(Lang.settings_player_mpv_options_count, optionCount)
                    },
                )
            },
            trailingContent = {
                IconButton({ showDialog = true }) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        if (showDialog) {
            val savedText = remember(config.mpvOptions) { config.mpvOptions.joinToString("\n") }
            var editingValue by rememberSaveable(savedText) { mutableStateOf(savedText) }
            TextFieldDialog(
                onDismissRequest = { showDialog = false },
                onConfirm = {
                    playerKernelConfig.update(config.copy(mpvOptions = splitMpvOptionLines(editingValue)))
                    showDialog = false
                },
                title = { Text(stringResource(Lang.settings_player_mpv_options)) },
                description = { Text(stringResource(Lang.settings_player_mpv_options_description)) },
            ) {

                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    modifier = Modifier.fillMaxWidth()
                        .testTag(MpvOptionsItemTestTags.TEXT_FIELD)
                        .defaultFocus(),
                    placeholder = { Text(stringResource(Lang.settings_player_mpv_options_placeholder)) },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 3,
                    maxLines = 5,
                )
            }
        }
    }
}
