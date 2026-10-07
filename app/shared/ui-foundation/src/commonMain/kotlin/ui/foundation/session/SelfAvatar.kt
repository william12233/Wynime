package com.wynime.app.ui.foundation.session

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.navigation.WynimeNavigator
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.tools.rememberUiMonoTasker
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.avatar.AvatarImage
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.login_sign_in
import com.wynime.app.ui.lang.playback_history_title
import com.wynime.app.ui.lang.settings_account_confirm_logout
import com.wynime.app.ui.lang.settings_account_logout
import com.wynime.app.ui.lang.settings_account_settings
import com.wynime.app.ui.lang.subject_collection_cancel
import com.wynime.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

@Composable
fun SelfAvatar(
    state: SelfInfoUiState,
    size: DpSize,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val signInText = stringResource(Lang.login_sign_in)

    @Composable
    fun AvatarSurface(content: @Composable () -> Unit) = if (onClick == null) {
        Surface(modifier, shape = CircleShape, content = content)
    } else {
        Surface(onClick, modifier, shape = CircleShape, content = content)
    }

    if (state.isLoading) {
        AvatarSurface {

            AvatarImage(
                url = state.selfInfo?.avatarUrl,
                Modifier.size(size).clip(CircleShape).placeholder(state.selfInfo == null),
            )
        }
    } else {
        if (state.isSessionValid == false || state.selfInfo == null) {
            TextButton(onClick ?: {}, modifier = modifier) {
                Text(signInText)
            }
        } else {
            AvatarSurface {
                AvatarImage(
                    url = state.selfInfo.avatarUrl,
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            }
        }
    }
}

@Stable
interface SelfAvatarActionHandler {
    fun onClickPlaybackHistory()
    fun onClickSettings()
    suspend fun onLogout()
}

private class DefaultSelfAvatarActionHandler(
    private val navigator: WynimeNavigator,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : SelfAvatarActionHandler, KoinComponent {
    private val userRepo: UserRepository by inject()
    override fun onClickPlaybackHistory() {
        navigator.navigatePlaybackHistory()
    }

    override fun onClickSettings() {
        navigator.navigateSettings()
    }

    override suspend fun onLogout() {
        withContext(dispatcher) {
            userRepo.clearSelfInfo()
        }
    }
}

@Composable
fun rememberSelfAvatarActionHandler(): SelfAvatarActionHandler {
    val navigator = LocalNavigator.current
    return remember(navigator) { DefaultSelfAvatarActionHandler(navigator) }
}

@Composable
private fun SelfAvatarMenus(
    handler: SelfAvatarActionHandler,
    onClickAny: () -> Unit,
) {
    val playbackHistoryText = stringResource(Lang.playback_history_title)
    val settingsText = stringResource(Lang.settings_account_settings)
    val logoutText = stringResource(Lang.settings_account_logout)
    val confirmLogoutText = stringResource(Lang.settings_account_confirm_logout)
    val cancelText = stringResource(Lang.subject_collection_cancel)

    DropdownMenuItem(
        text = { Text(playbackHistoryText) },
        onClick = {
            handler.onClickPlaybackHistory()
            onClickAny()
        },
        leadingIcon = { Icon(Icons.Rounded.History, null) },
    )

    DropdownMenuItem(
        text = { Text(settingsText) },
        onClick = {
            handler.onClickSettings()
            onClickAny()
        },
        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
    )

    val logoutTasker = rememberUiMonoTasker()
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    val running by logoutTasker.isRunning.collectAsStateWithLifecycle()
    DropdownMenuItem(
        text = { Text(logoutText, color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
        onClick = { showLogoutConfirmation = true },
        enabled = !running,
    )
    if (showLogoutConfirmation) {
        AlertDialog(
            { showLogoutConfirmation = false },
            text = { Text(confirmLogoutText) },
            confirmButton = {
                TextButton(
                    {
                        logoutTasker.launch {
                            handler.onLogout()
                            onClickAny()
                        }
                        showLogoutConfirmation = false
                    },
                ) {
                    Text(logoutText, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showLogoutConfirmation = false }) {
                    Text(cancelText)
                }
            },
        )
    }
}
