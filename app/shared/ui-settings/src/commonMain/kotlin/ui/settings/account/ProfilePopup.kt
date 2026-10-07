package com.wynime.app.ui.settings.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_account_popup_cancel
import com.wynime.app.ui.lang.settings_account_popup_logout_button
import com.wynime.app.ui.lang.settings_account_popup_logout_confirm
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfilePopup(
    vm: ProfileViewModel,
    onDismissRequest: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccountSettings: () -> Unit,
    onNavigateToPlaybackHistory: () -> Unit,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass,
) {
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val content = @Composable {
        ProfilePopupLayout(
            state,
            onClickLogin = onNavigateToLogin,
            onClickEditAvatar = onNavigateToAccountSettings,
            onClickEditProfile = onNavigateToAccountSettings,
            onClickPlaybackHistory = onNavigateToPlaybackHistory,
            onClickSettings = onNavigateToSettings,
            { showLogoutDialog = true },
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                .ifThen(windowSizeClass.isWidthAtLeastMedium) {
                    padding(horizontal = 8.dp)
                }
                .ifThen(windowSizeClass.isHeightAtLeastMedium) {
                    padding(vertical = 8.dp)
                },
        )
    }

    if (windowSizeClass.isWidthAtLeastMedium) {
        val density = LocalDensity.current
        Popup(
            alignment = Alignment.TopEnd,
            offset = with(density) {
                IntOffset(0, 32.dp.roundToPx())
            },
            properties = PopupProperties(),
            onDismissRequest = onDismissRequest,
        ) {

            Box(
                Modifier.fillMaxSize()
                    .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.TopEnd,
            ) {

                Surface(
                    modifier = Modifier
                        .windowInsetsPadding(WynimeWindowInsets.safeDrawing)
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 360.dp)
                        .clickable(interactionSource = null, indication = null, onClick = {}),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = BottomSheetDefaults.ContainerColor,
                    contentColor = contentColorFor(BottomSheetDefaults.ContainerColor),
                    tonalElevation = 0.dp,
                ) {
                    content()
                }
            }
        }

    } else {
        BasicAlertDialog(onDismissRequest) {
            Surface(
                modifier = Modifier,
                shape = MaterialTheme.shapes.extraLarge,
                color = BottomSheetDefaults.ContainerColor,
                contentColor = contentColorFor(BottomSheetDefaults.ContainerColor),
                tonalElevation = 0.dp,
            ) {
                content()
            }
        }
    }

    if (showLogoutDialog) {
        val asyncHandler = rememberAsyncHandler()
        AccountLogoutDialog(
            {
                asyncHandler.launch {
                    vm.logout()
                    showLogoutDialog = false
                }
            },
            onCancel = { showLogoutDialog = false },
            confirmEnabled = !asyncHandler.isWorking,
        )
    }
}

@Composable
fun AccountLogoutDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onCancel,
        icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
        text = { Text(stringResource(Lang.settings_account_popup_logout_confirm)) },
        confirmButton = {
            TextButton(onConfirm, enabled = confirmEnabled) {
                Text(stringResource(Lang.settings_account_popup_logout_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text(stringResource(Lang.settings_account_popup_cancel))
            }
        },
    )
}
