package com.wynime.app.ui.settings.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wynime.app.domain.session.auth.OAuthPlatform
import com.wynime.app.ui.foundation.avatar.AvatarImage
import com.wynime.app.ui.foundation.icons.BangumiNext
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastExpanded
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.foundation.widgets.HeroIcon
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_account_tracking_sync_description
import com.wynime.app.ui.lang.settings_account_tracking_sync_title
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.ProfileGroup(
    onNavigateToBangumiSync: () -> Unit,
    onNavigateToOAuth: (OAuthPlatform) -> Unit,
    vm: ProfileViewModel = viewModel<ProfileViewModel> { ProfileViewModel() },
    modifier: Modifier = Modifier,

) {
    val state by vm.stateFlow.collectAsStateWithLifecycle(initialValue = AccountSettingsState.Empty)
    val asyncHandler = rememberAsyncHandler()
    val selfInfo = state.selfInfo.selfInfo
    val isLoggedIn = state.selfInfo.isSessionValid == true

    Column(modifier) {
        HeroIcon(
            Modifier.padding(vertical = if (currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastExpanded) 36.dp else 24.dp),
        ) {
            AvatarImage(
                url = selfInfo?.avatarUrl,
                modifier = Modifier.clip(CircleShape),
            )
        }

        Group({ Text("Bangumi") }) {
            TextItem(
                title = { Text(if (isLoggedIn) "Bangumi 帳號" else "登入 Bangumi") },
                description = {
                    Text(
                        state.selfInfo.loadError?.let { selfInfoLoadErrorText(it) }
                            ?: selfInfo?.bangumiUsername
                            ?: "使用 Bangumi 帳號登入以同步收藏與播放進度。",
                    )
                },
                icon = { Image(Icons.Default.BangumiNext, contentDescription = "Bangumi") },
                onClick = if (isLoggedIn) null else { { onNavigateToOAuth(OAuthPlatform.BANGUMI) } },
                modifier = Modifier.testTag("bangumi-account"),
            )
            TextItem(
                title = { Text(stringResource(Lang.settings_account_tracking_sync_title)) },
                description = { Text(stringResource(Lang.settings_account_tracking_sync_description)) },
                onClick = onNavigateToBangumiSync,
                modifier = Modifier.testTag("bangumi-tracking-sync-entry"),
            )
            if (isLoggedIn) {
                TextItem(
                    title = { Text("登出") },
                    description = { Text("清除此裝置的 Bangumi session。") },
                    onClick = {
                        asyncHandler.launch { vm.logout() }
                    },
                    modifier = Modifier.testTag("bangumi-logout"),
                )
            }
        }
    }
}

