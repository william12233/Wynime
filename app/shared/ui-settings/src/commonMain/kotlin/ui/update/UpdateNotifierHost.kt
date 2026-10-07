package com.wynime.app.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.wynime.app.platform.LocalContext
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.utils.platform.annotations.TestOnly

@Composable
fun BoxScope.UpdateNotifier(
    viewModel: AppUpdateViewModel = viewModel { AppUpdateViewModel() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    layoutKind: UpdateNotifierLayoutKind = UpdateNotifierDefaults.layoutKind(),
) {
    SideEffect {

        viewModel.startAutomaticCheckLatestVersion()
    }

    val uriHandler = LocalUriHandler.current

    val presentation by viewModel.presentationFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAppResumed(context)
    }

    UpdateNotifier(
        presentation = presentation,
        onStartUpdateClick = {
            presentation.newVersion?.let {
                viewModel.startDownload(it, uriHandler, context)
            }
        },
        onInstallClick = {
            viewModel.install(context)
        },
        onCancelClick = {
            viewModel.cancelDownload()
        },
        onRetryClick = {
            viewModel.restartDownload(uriHandler, context)
        },
        snackbarHostState = snackbarHostState,
        layoutKind = layoutKind,
    )

    presentation.installationFailure?.let {
        FailedToInstallDialog(
            it.reason.toString(),
            onDismissRequest = {
                viewModel.dismissInstallationFailure()
            },
            state = presentation.state,
        )
    }
}

@Composable
fun BoxScope.UpdateNotifier(
    presentation: AppUpdatePresentation,
    onStartUpdateClick: () -> Unit,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    layoutKind: UpdateNotifierLayoutKind = UpdateNotifierDefaults.layoutKind(),
) {
    val uriHandler = LocalUriHandler.current

    var dismissedManually by rememberSaveable(
        presentation.newVersion?.name,
    ) {
        mutableStateOf(false)
    }

    val newVersion = presentation.newVersion
    if (newVersion != null) {
        if (presentation.isDownloading) {
            val positionModifiers = when (layoutKind) {
                UpdateNotifierLayoutKind.POPUP -> Modifier.padding(24.dp).align(Alignment.BottomEnd)
                UpdateNotifierLayoutKind.SNACKBAR -> Modifier.padding(16.dp).fillMaxWidth()
                    .align(Alignment.BottomCenter)
            }

            if (!dismissedManually) {
                DownloadingUpdatePopupCard(
                    version = presentation.newVersion,
                    fileDownloaderStats = presentation.fileDownloaderStats,
                    error = presentation.downloadError,
                    isInstalling = presentation.state is AppUpdateState.Installing,
                    onInstallClick = onInstallClick,
                    onCancelClick = {
                        onCancelClick()
                        if (presentation.state !is AppUpdateState.Installing) {
                            dismissedManually = true
                        }
                    },
                    onRetryClick = onRetryClick,
                    modifier = positionModifiers,
                )
            }
        } else {

            val onDetailsClick =
                { uriHandler.openUri("https://github.com/william12233/Wynime/releases/tag/${newVersion.name}") }

            when (layoutKind) {
                UpdateNotifierLayoutKind.POPUP -> {
                    if (!dismissedManually) {
                        DesktopPopup(
                            version = newVersion,
                            onDismiss = { dismissedManually = true },
                            onDetailsClick,
                            onAutoUpdateClick = onStartUpdateClick,
                            modifier = Modifier.align(Alignment.BottomEnd)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(24.dp)
                                .shadow(4.dp, MaterialTheme.shapes.extraLarge),
                        )
                    }
                }

                UpdateNotifierLayoutKind.SNACKBAR -> {

                    var showDetails by rememberSaveable { mutableStateOf(false) }

                    if (!dismissedManually) {
                        MobileSnackbar(
                            hostState = snackbarHostState,
                            version = newVersion,
                            onShowDetailsClick = { showDetails = true },
                            onDismissChanged = { dismissedManually = it },
                        )
                    }

                    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

                    if (showDetails && !dismissedManually) {
                        BasicAlertDialog(
                            { dismissedManually = true },
                        ) {
                            NewVersionPopupCard(
                                version = newVersion.name,
                                changes = newVersion.majorChanges,
                                onDetailsClick = onDetailsClick,
                                onAutoUpdateClick = onStartUpdateClick,
                                onDismissRequest = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class UpdateNotifierLayoutKind {
    POPUP,
    SNACKBAR,
}

object UpdateNotifierDefaults {
    @Composable
    fun layoutKind(windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass): UpdateNotifierLayoutKind {
        return if (windowSizeClass.isWidthAtLeastMedium && windowSizeClass.isHeightAtLeastMedium) {
            UpdateNotifierLayoutKind.POPUP
        } else {
            UpdateNotifierLayoutKind.SNACKBAR
        }
    }
}

@Composable
private fun BoxScope.DesktopPopup(
    version: NewVersion,
    onDismiss: () -> Unit,
    onDetailsClick: () -> Unit,
    onAutoUpdateClick: () -> Unit,
    modifier: Modifier,
) {
    WynimeAnimatedVisibility(
        visible = true,
        modifier = modifier.align(Alignment.BottomEnd),
    ) {
        NewVersionPopupCard(
            version = version.name,
            changes = version.majorChanges,
            onDetailsClick = onDetailsClick,
            onAutoUpdateClick = onAutoUpdateClick,
            onDismissRequest = onDismiss,
        )
    }
}

@Composable
private fun MobileSnackbar(
    hostState: SnackbarHostState,
    version: NewVersion,
    onShowDetailsClick: () -> Unit,
    onDismissChanged: (Boolean) -> Unit,
) {
    LaunchedEffect(version.name) {
        val result = hostState.showSnackbar(
            message = buildString {
                append("新版本 ${version.name}")

                version.majorChanges.firstOrNull()?.let {
                    append("：$it")
                }
            },
            actionLabel = "查看",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> {
                onShowDetailsClick()
            }

            SnackbarResult.Dismissed -> onDismissChanged(true)
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewUpdateNotifierHostPopup() = ProvideCompositionLocalsForPreview {
    PreviewImpl(UpdateNotifierLayoutKind.POPUP)
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewUpdateNotifierHostPopupDownloading() = ProvideCompositionLocalsForPreview {
    PreviewImpl(
        UpdateNotifierLayoutKind.POPUP,
        state = TestAppUpdatePresentations.Downloading,
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewUpdateNotifierHostSnackbar() = ProvideCompositionLocalsForPreview {
    PreviewImpl(UpdateNotifierLayoutKind.SNACKBAR)
}

@TestOnly
@Composable
private fun PreviewImpl(
    kind: UpdateNotifierLayoutKind,
    state: AppUpdatePresentation = TestAppUpdatePresentations.HasUpdate,
) {
    Box {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(10) {
                ListItem(headlineContent = { Text("Test $it") })
            }
        }

        UpdateNotifier(
            presentation = state,
            onStartUpdateClick = {},
            {},
            {},
            {},
            layoutKind = kind,
        )
    }
}
