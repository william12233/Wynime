package com.wynime.app.ui.download.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_management_pause_selected
import com.wynime.app.ui.lang.cache_management_resume_selected
import com.wynime.app.ui.lang.cache_subject_delete
import org.jetbrains.compose.resources.stringResource

object DownloadSelectionToolbarTestTags {
    const val RESUME = "cache_selection_toolbar_resume"
    const val PAUSE = "cache_selection_toolbar_pause"
    const val DELETE = "cache_selection_toolbar_delete"
}

@Composable
internal fun DownloadSelectionFloatingToolbar(
    resumeEnabled: Boolean,
    pauseEnabled: Boolean,
    deleteEnabled: Boolean,
    onResumeSelected: () -> Unit,
    onPauseSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .windowInsetsPadding(windowInsets)
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onResumeSelected,
                    enabled = resumeEnabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag(DownloadSelectionToolbarTestTags.RESUME),
                ) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(Lang.cache_management_resume_selected))
                }
                IconButton(
                    onClick = onPauseSelected,
                    enabled = pauseEnabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag(DownloadSelectionToolbarTestTags.PAUSE),
                ) {
                    Icon(Icons.Rounded.Pause, stringResource(Lang.cache_management_pause_selected))
                }
                VerticalDivider(
                    Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                IconButton(
                    onClick = onDeleteSelected,
                    enabled = deleteEnabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(DownloadSelectionToolbarTestTags.DELETE),
                ) {
                    Icon(Icons.Rounded.Delete, stringResource(Lang.cache_subject_delete))
                }
            }
        }
    }
}
