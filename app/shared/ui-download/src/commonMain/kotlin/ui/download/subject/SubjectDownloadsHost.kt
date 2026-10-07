package com.wynime.app.ui.download.subject

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.PermissionManager
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_subject_cancel
import com.wynime.app.ui.lang.downloads_operation_failed
import com.wynime.app.ui.mediafetch.MediaSourceInfoProvider
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
internal fun SubjectDownloadsHost(
    presenter: SubjectDownloadsPresenter,
    content: @Composable (SubjectDownloadsUiState, SubjectDownloadActions, MediaSourceInfoProvider) -> Unit,
) {
    val state by presenter.uiState.collectAsStateWithLifecycle()
    val request by presenter.requestDialogs.collectAsStateWithLifecycle()
    val failedOperations by presenter.operationFailures.collectAsStateWithLifecycle()
    var pickerVisible by remember(presenter, request) { mutableStateOf(true) }
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val permissionManager = remember { KoinPlatform.getKoin().get<PermissionManager>() }
    val actions = SubjectDownloadActions(
        download = { episodeId ->
            if (presenter.requestDownload(episodeId)) {
                uiScope.launch { permissionManager.requestNotificationPermission(context) }
            }
            pickerVisible = true
        },
        cancelRequest = presenter::cancelRequest,
        pause = presenter::pauseDownloads,
        resume = presenter::resumeDownloads,
        delete = presenter::deleteDownloads,
        pauseAll = presenter::pauseAll,
        resumeAll = presenter::resumeAll,
        reload = presenter::reload,
    )
    request?.let { dialogState ->
        SubjectDownloadRequestDialogs(
            state = dialogState,
            visible = pickerVisible,
            sourceInfoProvider = presenter.sourceInfoProvider,
            settings = presenter.selectorSettings,
            onHide = { pickerVisible = false },
            onSelectMedia = presenter::selectMedia,
            onConfirmEpisodes = presenter::confirmEpisodes,
            onBackToMediaSelection = presenter::backToMediaSelection,
            onCancel = presenter::cancelRequest,
        )
    }
    if (failedOperations > 0) {
        AlertDialog(
            onDismissRequest = presenter::dismissOperationFailures,
            text = { Text(stringResource(Lang.downloads_operation_failed, failedOperations)) },
            confirmButton = { TextButton(onClick = presenter::dismissOperationFailures) { Text(stringResource(Lang.cache_subject_cancel)) } },
        )
    }
    content(state, actions, presenter.sourceInfoProvider)
}
