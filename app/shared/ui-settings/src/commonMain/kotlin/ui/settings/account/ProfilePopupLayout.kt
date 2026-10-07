package com.wynime.app.ui.settings.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.user.calculateDisplay
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.IconButton
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.avatar.AvatarImage
import com.wynime.app.ui.foundation.interaction.hoverable
import com.wynime.app.ui.foundation.text.ProvideContentColor
import com.wynime.app.ui.foundation.widgets.HeroIcon
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.playback_history_title
import com.wynime.app.ui.lang.settings
import com.wynime.app.ui.lang.settings_account_popup_edit_profile
import com.wynime.app.ui.lang.settings_account_popup_login_register
import com.wynime.app.ui.lang.settings_account_popup_logout
import com.wynime.app.ui.lang.settings_account_popup_not_logged_in
import com.wynime.app.ui.settings.SettingsTab
import com.wynime.app.ui.settings.framework.components.TextItem
import com.wynime.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProfilePopupLayout(
    state: AccountSettingsState,
    onClickLogin: () -> Unit,
    onClickEditAvatar: () -> Unit,
    onClickEditProfile: () -> Unit,
    onClickPlaybackHistory: () -> Unit,
    onClickSettings: () -> Unit,
    onClickLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLogin = remember(state) { state.selfInfo.isSessionValid == true }
    val notLoggedInText = stringResource(Lang.settings_account_popup_not_logged_in)
    val editProfileText = stringResource(Lang.settings_account_popup_edit_profile)
    val loginRegisterText = stringResource(Lang.settings_account_popup_login_register)
    val playbackHistoryText = stringResource(Lang.playback_history_title)
    val settingsText = stringResource(Lang.settings)
    val logoutText = stringResource(Lang.settings_account_popup_logout)
    Column(modifier) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HeroIcon {
                AvatarImage(
                    url = state.selfInfo.selfInfo?.avatarUrl?.takeIf { isLogin },
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .placeholder(state.selfInfo.isLoading),
                )
            }
        }
        val (title, _) = state.selfInfo.selfInfo.calculateDisplay()
        val showEmail = false

        Text(
            if (isLogin) title else notLoggedInText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(
                    bottom = if (showEmail) 4.dp else 0.dp,
                )
                .fillMaxWidth(),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.MiddleEllipsis,
        )
        if (showEmail) {
            Text(
                remember(state) {
                    state.selfInfo.selfInfo?.email ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 2.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }

        SettingsTab(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column {
                if (isLogin) {
                    TextItem(
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = editProfileText) },
                        onClick = onClickEditProfile,
                    ) {
                        Text(editProfileText)
                    }
                } else {
                    TextItem(
                        icon = { Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = loginRegisterText) },
                        onClick = onClickLogin,
                    ) {
                        Text(loginRegisterText)
                    }
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.History, contentDescription = playbackHistoryText) },
                    onClick = onClickPlaybackHistory,
                ) {
                    Text(playbackHistoryText)
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = settingsText) },
                    onClick = onClickSettings,
                ) {
                    Text(settingsText)
                }

                if (isLogin) {
                    TextItem(
                        icon = {
                            ProvideContentColor(MaterialTheme.colorScheme.error) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = logoutText,
                                )
                            }
                        },
                        onClick = onClickLogout,
                    ) {
                        ProvideContentColor(MaterialTheme.colorScheme.error) {
                            Text(logoutText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableSelfAvatar(
    selfInfo: SelfInfoUiState,
    onClickEditAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(96.dp, 96.dp),
) {
    var showEditAvatarScrim by remember { mutableStateOf(false) }

    Box(
        modifier
            .size(size)
            .hoverable(
                onHover = { showEditAvatarScrim = true },
                onUnhover = { showEditAvatarScrim = false },
            ),
    ) {
        AvatarImage(
            url = selfInfo.selfInfo?.avatarUrl,
            Modifier
                .size(size)
                .clip(CircleShape)
                .placeholder(selfInfo.isLoading),
        )
        WynimeAnimatedVisibility(
            showEditAvatarScrim,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(DrawerDefaults.scrimColor),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    IconButton(onClickEditAvatar) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit avatar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewAccountSettingsPopupLayout() {
    ProvideCompositionLocalsForPreview {
        Surface {
            ProfilePopupLayout(
                TestAccountSettingsState,
                { },
                { },
                { },
                { },
                { },
                { },
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}
