package com.wynime.app.ui.foundation.feedback

import androidx.annotation.UiThread
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_error_dialog_copy_prefix
import com.wynime.app.ui.lang.foundation_error_dialog_default_message
import com.wynime.app.ui.lang.settings_account_profile_ok
import com.wynime.app.ui.lang.settings_mediasource_copy
import com.wynime.app.ui.lang.subject_collection_cancel
import com.wynime.app.ui.loading.ConnectingDialog
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

interface ErrorDialogController {
    val isVisible: Boolean
        @Composable get

    fun hide()

    fun show()

    val debugInfo: String?
        @Composable get

    fun setDebugInfo(debugInfo: String?)
}

class StateErrorDialogController : ErrorDialogController {
    private var _isVisible: Boolean by mutableStateOf(false)

    override val isVisible: Boolean
        @Composable get() = _isVisible

    override fun hide() {
        _isVisible = false
    }

    override fun show() {
        _isVisible = true
    }

    private var _debugInfo: String? by mutableStateOf("")
    override val debugInfo: String?
        @Composable get() = _debugInfo

    override fun setDebugInfo(debugInfo: String?) {
        _debugInfo = debugInfo
    }
}

@Composable
fun ErrorDialogHost(
    errorFlow: MutableStateFlow<ErrorMessage?>,
    onClickCancel: () -> Unit = {},
    onConfirm: () -> Unit = {
        errorFlow.value = null
    },
) {
    return ErrorDialogHost(
        errorFlow = errorFlow as Flow<ErrorMessage?>,
        onClickCancel = onClickCancel,
        onConfirm = onConfirm,
    )
}

@Composable
fun ErrorDialogHost(
    errorFlow: Flow<ErrorMessage?>,
    onClickCancel: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    val controller = remember {
        StateErrorDialogController()
    }
    val defaultErrorMessage = stringResource(Lang.foundation_error_dialog_default_message)
    val copyPrefixText = stringResource(Lang.foundation_error_dialog_copy_prefix)
    val copyText = stringResource(Lang.settings_mediasource_copy)
    val confirmText = stringResource(Lang.settings_account_profile_ok)
    val cancelText = stringResource(Lang.subject_collection_cancel)

    val error = remember(errorFlow) {
        errorFlow.distinctUntilChanged()
            .debounce(0.5.seconds)
    }.collectAsStateWithLifecycle(null).value

    LaunchedEffect(error) {
        if (error != null) {
            controller.show()
        } else {
            controller.hide()
        }
    }

    if (controller.isVisible) {
        ConnectingDialog(
            text = {
                Text(text = error?.message ?: defaultErrorMessage)
                val cause = error?.cause
                if (cause != null) {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text(text = cause.stackTraceToString())
                    }
                }
            },
            progress = if (error?.isRecovering == true) {
                {
                    LinearProgressIndicator(Modifier.width(128.dp))
                }
            } else null,
            onDismissRequest = {
                if (error?.isRecovering == false) {
                    controller.hide()
                }
            },
            confirmButton = {
                if (error?.isRecovering == false) {
                    if (error.cause != null) {
                        val clipboard = LocalClipboard.current
                        val scope = rememberCoroutineScope()
                        TextButton(
                            onClick = {
                                val copyTarget =
                                    (error?.message ?: copyPrefixText) + "\n\n" + error.cause?.stackTraceToString()
                                scope.launch {
                                    clipboard.setClipEntryText(copyTarget)
                                }
                            },
                        ) {
                            Text(copyText)
                        }
                    }
                    TextButton(
                        onClick = {
                            controller.hide()
                            error.onConfirm?.invoke()
                            onConfirm()
                        },
                    ) {
                        Text(confirmText)
                    }
                } else {

                    TextButton(
                        onClick = {
                            controller.hide()
                            error?.onCancel?.invoke()
                            onClickCancel()
                        },
                    ) {
                        Text(cancelText)
                    }
                }
            },
        )
    }
}

@Stable
interface ErrorMessage {
    val message: String?
    val cause: Throwable?

    val isRecovering: Boolean

    val onConfirm: (() -> Unit)?
    val onCancel: (() -> Unit)?

    companion object Factory {

        fun networkErrorRecovering(cause: Throwable? = null): ErrorMessage =
            SimpleErrorMessage("Connection lost, reconnecting...", cause, isRecovering = true)

        fun networkError(cause: Throwable? = null): ErrorMessage =
            SimpleErrorMessage("Network error, please check your connection and try again", cause)

        fun simple(
            message: String?,
            cause: Throwable? = null,
            @UiThread onConfirm: (() -> Unit)? = null
        ): ErrorMessage =
            SimpleErrorMessage(message, cause, onConfirm = onConfirm)

        fun processing(
            message: String?,
            cause: Throwable? = null,
            @UiThread onCancel: (() -> Unit)? = null
        ): ErrorMessage =
            SimpleErrorMessage(message, cause, isRecovering = true, onCancel = onCancel)
    }

    private class SimpleErrorMessage(
        override val message: String?,
        override val cause: Throwable? = null,
        override val isRecovering: Boolean = false,
        override val onConfirm: (() -> Unit)? = null,
        override val onCancel: (() -> Unit)? = null,
    ) : ErrorMessage
}
