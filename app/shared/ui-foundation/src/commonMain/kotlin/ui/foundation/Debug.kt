package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.preference.DebugSettings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface DebugSettingsViewModel {
    @TestOnly
    var isAppInDebugModeOverride: Boolean
    val isAppInDebugMode: Boolean
    val showControllerAlwaysOnRequesters: Boolean

    val debugSettings: State<DebugSettings>
    fun updateDebugSettings(settings: DebugSettings)
}

class DebugSettingsViewModelImpl : DebugSettingsViewModel, AbstractViewModel(), KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()
    override val debugSettings by lazy { settingsRepository.debugSettings.flow.produceState(DebugSettings(_placeHolder = -1)) }

    override fun updateDebugSettings(settings: DebugSettings) {
        launchInBackground {
            settingsRepository.debugSettings.set(settings)
        }
    }

    @TestOnly
    override var isAppInDebugModeOverride by mutableStateOf(false)

    @OptIn(TestOnly::class)
    override val isAppInDebugMode: Boolean by derivedStateOf {
        isAppInDebugModeOverride || debugSettings.value.enabled
    }
    override val showControllerAlwaysOnRequesters: Boolean
            by settingsRepository.debugSettings.flow.map { it.showControllerAlwaysOnRequesters }.produceState(false)
}

class PreviewDebugSettingsViewModel : DebugSettingsViewModel {
    @TestOnly
    override var isAppInDebugModeOverride: Boolean = true

    @OptIn(TestOnly::class)
    override val isAppInDebugMode: Boolean
        get() = isAppInDebugModeOverride
    override val showControllerAlwaysOnRequesters: Boolean
        get() = true

    override val debugSettings: MutableState<DebugSettings> = mutableStateOf(DebugSettings(_placeHolder = -1))

    override fun updateDebugSettings(settings: DebugSettings) {
        debugSettings.value = settings
    }
}

@Composable
fun isInDebugMode(): Boolean {
    val vm = rememberDebugSettingsViewModel()
    return vm.isAppInDebugMode
}

@Composable
fun rememberDebugSettingsViewModel(): DebugSettingsViewModel {
    return if (LocalIsPreviewing.current) {
        remember { PreviewDebugSettingsViewModel() }
    } else {
        viewModel<DebugSettingsViewModelImpl> { DebugSettingsViewModelImpl() }
    }
}