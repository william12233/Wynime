package com.wynime.app.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.tools.update.FileDownloaderState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_update_popup_cancel
import com.wynime.app.ui.lang.settings_update_popup_cancel_download
import com.wynime.app.ui.lang.settings_update_popup_cancel_install
import com.wynime.app.ui.lang.settings_update_popup_continue_download
import com.wynime.app.ui.lang.settings_update_popup_continue_update
import com.wynime.app.ui.lang.settings_update_popup_download_complete
import com.wynime.app.ui.lang.settings_update_popup_downloading
import com.wynime.app.ui.lang.settings_update_popup_installing
import com.wynime.app.ui.lang.settings_update_popup_restart_update
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadingUpdatePopupCard(
    version: NewVersion,
    fileDownloaderStats: FileDownloaderStats,
    error: LoadError?,
    isInstalling: Boolean,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirmCancel by rememberSaveable { mutableStateOf(false) }
    val onRequestCancel = {

        if (isInstalling) {
            showConfirmCancel = true
        } else when (fileDownloaderStats.state) {
            FileDownloaderState.Downloading -> showConfirmCancel = true
            is FileDownloaderState.Succeed,
            is FileDownloaderState.Failed,
            is FileDownloaderState.Cancelled,
            FileDownloaderState.Idle -> onCancelClick()
        }
    }

    if (showConfirmCancel) {
        AlertDialog(
            onDismissRequest = { showConfirmCancel = false },
            text = {
                Text(
                    stringResource(
                        if (isInstalling) Lang.settings_update_popup_cancel_install
                        else Lang.settings_update_popup_cancel_download,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancelClick()
                        showConfirmCancel = false
                    },
                ) {
                    Text(stringResource(Lang.settings_update_popup_cancel))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmCancel = false },
                ) {
                    Text(
                        stringResource(
                            if (isInstalling) Lang.settings_update_popup_continue_update
                            else Lang.settings_update_popup_continue_download,
                        ),
                    )
                }
            },
        )
    }

    BasicNotificationPopupCard(
        title = {
            Text(
                stringResource(
                    if (isInstalling) Lang.settings_update_popup_installing
                    else Lang.settings_update_popup_downloading,
                ),
            )
        },
        modifier,
        dismissButton = {
            NotificationPopupDefaults.DismissButton(onRequestCancel)
        },
        subtitle = { Text(version.name) },
        actions = {
            if (!isInstalling && fileDownloaderStats.state is FileDownloaderState.Succeed) {
                Button(
                    onClick = onInstallClick,
                ) {
                    Text(stringResource(Lang.settings_update_popup_restart_update))
                }
            }
        },
    ) {
        when {
            !isInstalling && fileDownloaderStats.state is FileDownloaderState.Succeed -> {
                ListItem(
                    headlineContent = {
                        Text(stringResource(Lang.settings_update_popup_download_complete))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.DownloadDone, null,
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }

            !isInstalling && error != null -> {
                LoadErrorCard(
                    error,
                    onRetry = onRetryClick,
                    elevation = CardDefaults.cardElevation(),
                )

            }

            else -> {
                val progress = if (isInstalling) null else fileDownloaderStats.progress
                val indicatorModifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 8.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                ListItem(
                    headlineContent = {
                        if (progress == null) {
                            LinearProgressIndicator(modifier = indicatorModifier)
                        } else {
                            LinearProgressIndicator(progress = { progress }, modifier = indicatorModifier)
                        }
                    },
                    trailingContent = progress?.let {
                        {
                            Box(Modifier.padding(start = 16.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(
                                    "${999}%",
                                    Modifier.alpha(0f),
                                )
                                Text(
                                    "${(progress * 100).fastRoundToInt()}%",
                                    Modifier,
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewDownloadingUpdatePopupCard() = ProvideCompositionLocalsForPreview {
    DownloadingUpdatePopupCard(
        version = TestNewVersion,
        fileDownloaderStats = TestFileDownloaderStats.Downloading,
        error = null,
        isInstalling = false,
        {}, {}, {},
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewDownloadingUpdatePopupCardError() = ProvideCompositionLocalsForPreview {
    DownloadingUpdatePopupCard(
        version = TestNewVersion,
        fileDownloaderStats = TestFileDownloaderStats.Failed,
        error = LoadError.NetworkError,
        isInstalling = false,
        {}, {}, {},
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewDownloadingUpdatePopupCardSuccess() = ProvideCompositionLocalsForPreview {
    DownloadingUpdatePopupCard(
        version = TestNewVersion,
        fileDownloaderStats = TestFileDownloaderStats.Succeed,
        error = null,
        isInstalling = false,
        {}, {}, {},
    )
}
