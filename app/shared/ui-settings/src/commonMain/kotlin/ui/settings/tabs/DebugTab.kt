package com.wynime.app.ui.settings.tabs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.wynime.app.data.models.preference.DebugSettings
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.domain.session.SessionManager
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.app.platform.MeteredNetworkDetector
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_debug_copied
import com.wynime.app.ui.lang.settings_debug_dev_builds
import com.wynime.app.ui.lang.settings_debug_dev_builds_install_commit
import com.wynime.app.ui.lang.settings_debug_dev_builds_install_commit_description
import com.wynime.app.ui.lang.settings_debug_episodes
import com.wynime.app.ui.lang.settings_debug_get_ani_token
import com.wynime.app.ui.lang.settings_debug_install_package
import com.wynime.app.ui.lang.settings_debug_install_package_on_drop
import com.wynime.app.ui.lang.settings_debug_install_package_on_drop_description
import com.wynime.app.ui.lang.settings_debug_logged_out
import com.wynime.app.ui.lang.settings_debug_logout
import com.wynime.app.ui.lang.settings_debug_metered_network
import com.wynime.app.ui.lang.settings_debug_mode
import com.wynime.app.ui.lang.settings_debug_mode_description
import com.wynime.app.ui.lang.settings_debug_others
import com.wynime.app.ui.lang.settings_debug_show_all_episodes
import com.wynime.app.ui.lang.settings_debug_show_all_episodes_description
import com.wynime.app.ui.lang.settings_debug_status
import com.wynime.app.ui.settings.SettingsTab
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.framework.components.SwitchItem
import com.wynime.app.ui.settings.framework.components.TextItem
import com.wynime.app.ui.update.devbuild.DevBuildPackageSpec
import com.wynime.utils.platform.isDesktop
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
fun DebugTab(
    debugSettingsState: SettingsState<DebugSettings>,
    modifier: Modifier = Modifier,
    onDisableDebugMode: () -> Unit = {},
    onNavigateToDevBuilds: () -> Unit = {},
) {
    val debugSettings by debugSettingsState
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    SettingsTab(modifier) {
        Group(
            title = { Text(stringResource(Lang.settings_debug_status)) },
            useThinHeader = true,
        ) {
            SwitchItem(
                checked = debugSettings.enabled,
                onCheckedChange = { checked ->
                    if (!checked) onDisableDebugMode()
                    debugSettingsState.update(debugSettings.copy(enabled = checked))
                },
                title = { Text(stringResource(Lang.settings_debug_mode)) },
                description = { Text(stringResource(Lang.settings_debug_mode_description)) },
            )
        }
        Group(
            title = { Text(stringResource(Lang.settings_debug_episodes)) },
            useThinHeader = true,
        ) {
            SwitchItem(
                checked = debugSettings.showAllEpisodes,
                onCheckedChange = { checked ->
                    debugSettingsState.update(debugSettings.copy(showAllEpisodes = checked))
                },
                title = { Text(stringResource(Lang.settings_debug_show_all_episodes)) },
                description = { Text(stringResource(Lang.settings_debug_show_all_episodes_description)) },
            )
        }
        val installablePackageExtensions = remember { GlobalKoin.get<UpdateInstaller>().installablePackageExtensions }
        if (LocalPlatform.current.isDesktop() && installablePackageExtensions.isNotEmpty()) {
            Group(title = { Text(stringResource(Lang.settings_debug_install_package)) }, useThinHeader = true) {
                SwitchItem(
                    checked = debugSettings.installPackageOnDrop,
                    onCheckedChange = { checked ->
                        debugSettingsState.update(debugSettings.copy(installPackageOnDrop = checked))
                    },
                    title = { Text(stringResource(Lang.settings_debug_install_package_on_drop)) },
                    description = {
                        Text(
                            stringResource(
                                Lang.settings_debug_install_package_on_drop_description,
                                installablePackageExtensions.joinToString(", "),
                            ),
                        )
                    },
                )
            }
        }
        val platform = LocalPlatform.current
        val supportsDevBuilds = remember(platform) { DevBuildPackageSpec.forPlatform(platform) != null }
        if (supportsDevBuilds) {
            Group(title = { Text(stringResource(Lang.settings_debug_dev_builds)) }, useThinHeader = true) {
                TextItem(
                    title = { Text(stringResource(Lang.settings_debug_dev_builds_install_commit)) },
                    description = { Text(stringResource(Lang.settings_debug_dev_builds_install_commit_description)) },
                    onClick = onNavigateToDevBuilds,
                )
            }
        }
        Group(title = { Text(stringResource(Lang.settings_debug_metered_network)) }, useThinHeader = true) {
            TextItem {
                val networkDetector = KoinPlatform.getKoin().get<MeteredNetworkDetector>()
                val isMetered by networkDetector.isMeteredNetworkFlow.collectAsStateWithLifecycle(false)
                Text("isMetered: $isMetered")
            }
        }
        Group(title = { Text(stringResource(Lang.settings_debug_others)) }, useThinHeader = true) {
            TextItem(
                title = { Text(stringResource(Lang.settings_debug_logout)) },
                onClick = {
                    scope.launch {
                        GlobalKoin.get<UserRepository>().clearSelfInfo()
                        toaster.toast(getString(Lang.settings_debug_logged_out))
                    }
                },
            )
            TextItem(
                title = { Text(stringResource(Lang.settings_debug_get_ani_token)) },
                onClick = {
                    scope.launch {
                        val value =
                            (GlobalKoin.get<SessionManager>().sessionFlow.value as? AccessTokenSession)?.tokens?.legacyServiceAccessToken
                        toaster.toast(getString(Lang.settings_debug_copied, value.toString()))
                        clipboard.setClipEntryText(value.toString())
                    }
                },
            )
            TextItem(
                title = { Text("Crash") },
                onClick = {
                    throw ManualCrashException()
                },
            )
        }
    }
}

private class ManualCrashException : Throwable("Manual crash for testing")
