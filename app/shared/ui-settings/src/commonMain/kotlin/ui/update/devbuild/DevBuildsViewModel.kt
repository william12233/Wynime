package com.wynime.app.ui.update.devbuild

import androidx.compose.runtime.Stable
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.update.UpdateManager
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.utils.ktor.getPlatformKtorEngine
import com.wynime.utils.platform.currentPlatform
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class DevBuildsViewModel : AbstractViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val updateManager: UpdateManager by inject()
    private val installer: UpdateInstaller by inject()

    private val client = HttpClient(getPlatformKtorEngine()) {

        followRedirects = false
        expectSuccess = false
    }

    val state: DevBuildsState? = DevBuildPackageSpec.forPlatform(currentPlatform())?.let { spec ->
        DevBuildsState(
            api = GitHubDevBuildApi(client),
            spec = spec,
            installer = installer,
            saveDir = updateManager.devBuildsDir,
            getToken = { settingsRepository.debugSettings.flow.first().devBuildGitHubToken },
            currentVersionName = currentWynimeBuildConfig.versionName,
            backgroundScope = backgroundScope,
        )
    }

    val gitHubToken: StateFlow<String> = settingsRepository.debugSettings.flow
        .map { it.devBuildGitHubToken }
        .stateInBackground("")

    fun setGitHubToken(token: String) {
        backgroundScope.launch {
            settingsRepository.debugSettings.update { copy(devBuildGitHubToken = token.trim()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}
