package com.wynime.app.ui.settings

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.wynime.app.data.models.preference.AnalyticsSettings
import com.wynime.app.data.models.preference.DebugSettings
import com.wynime.app.data.models.preference.MediaCacheSettings
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.preference.OneshotActionConfig
import com.wynime.app.data.models.preference.PlayerKernelConfig
import com.wynime.app.data.models.preference.ProfileSettings
import com.wynime.app.data.models.preference.ProxyMode
import com.wynime.app.data.models.preference.ProxySettings
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.data.models.preference.UISettings
import com.wynime.app.data.models.preference.UpdateSettings
import com.wynime.app.data.models.preference.VideoResolverSettings
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.persistent.dataStores
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.sourceplugin.SourcePluginRegistry
import com.wynime.app.domain.sourceplugin.SourcePluginRepositoryClient
import com.wynime.app.domain.settings.ProxySettingsFlowProxyProvider
import com.wynime.app.domain.settings.ProxyTester
import com.wynime.app.domain.settings.ServiceConnectionTester
import com.wynime.app.domain.settings.ServiceConnectionTesters
import com.wynime.app.platform.PermissionManager
import com.wynime.app.platform.Context
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.ui.foundation.launchInBackground
import com.wynime.app.ui.settings.framework.AbstractSettingsViewModel
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.tabs.about.AboutTabInfo
import com.wynime.app.ui.settings.tabs.app.SoftwareUpdateGroupState
import com.wynime.app.ui.settings.tabs.media.CacheDirectoryGroupState
import com.wynime.app.ui.settings.tabs.media.MediaSelectionGroupState
import com.wynime.app.ui.settings.tabs.media.source.SourcePluginStoreState
import com.wynime.app.ui.settings.tabs.network.ConfigureProxyState
import com.wynime.app.ui.settings.tabs.network.ConfigureProxyUIState
import com.wynime.app.ui.settings.tabs.network.ProxyTestCase
import com.wynime.app.ui.settings.tabs.network.ProxyTestCaseState
import com.wynime.app.ui.settings.tabs.network.ProxyTestItem
import com.wynime.app.ui.settings.tabs.network.ProxyTestState
import com.wynime.app.ui.settings.tabs.network.SystemProxyPresentation
import com.wynime.app.ui.settings.tabs.network.toDataSettings
import com.wynime.app.ui.settings.tabs.network.toUIConfig
import com.wynime.app.ui.user.SelfInfoStateProducer
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.SingleTaskExecutor
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

open class SettingsViewModel : AbstractSettingsViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val permissionManager: PermissionManager by inject()

    private val clientProvider: HttpClientProvider by inject()
    private val tokenRepository: TokenRepository by inject()
    private val sourcePluginRepositoryClient: SourcePluginRepositoryClient by inject()
    private val sourcePluginRegistry: SourcePluginRegistry by inject()

    private val proxyProvider = ProxySettingsFlowProxyProvider(settingsRepository.proxySettings.flow, backgroundScope)

    private val loopTasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    val softwareUpdateGroupState: SoftwareUpdateGroupState = SoftwareUpdateGroupState(
        updateSettings = settingsRepository.updateSettings.stateInBackground(UpdateSettings.Default.copy(_placeholder = -1)),
    )

    val uiSettings: SettingsState<UISettings> =
        settingsRepository.uiSettings.stateInBackground(UISettings.Default.copy(_placeholder = -1))

    val themeSettings: SettingsState<ThemeSettings> =
        settingsRepository.themeSettings.stateInBackground(ThemeSettings.Default.copy(_placeholder = -1))

    val videoScaffoldConfig: SettingsState<VideoScaffoldConfig> =
        settingsRepository.videoScaffoldConfig.stateInBackground(VideoScaffoldConfig.Default.copy(_placeholder = -1))

    val playerKernelConfig: SettingsState<PlayerKernelConfig> =
        settingsRepository.playerKernelConfig.stateInBackground(PlayerKernelConfig.Default.copy(_placeholder = -1))

    val videoResolverSettingsState: SettingsState<VideoResolverSettings> =
        settingsRepository.videoResolverSettings.stateInBackground(VideoResolverSettings.Default.copy(_placeholder = -1))

    val mediaCacheSettingsState: SettingsState<MediaCacheSettings> =
        settingsRepository.mediaCacheSettings.stateInBackground(MediaCacheSettings.Default.copy(_placeholder = -1))

    val cacheDirectoryGroupState = CacheDirectoryGroupState(
        mediaCacheSettingsState,
        permissionManager,
        onGetBackupData = {
            withContext(Dispatchers.IO_) {
                serializeSettingsBackup()
            }
        },
        onRestoreSettings = {
            withContext(Dispatchers.IO_) {
                restoreSettingsBackup(it)
            }
        },
    )

    internal val mediaSelectorSettingsState: SettingsState<MediaSelectorSettings> =
        settingsRepository.mediaSelectorSettings.stateInBackground(MediaSelectorSettings.Default.copy(_placeholder = -1))

    private val defaultMediaPreferenceState =
        settingsRepository.defaultMediaPreference.stateInBackground(MediaPreference.PlatformDefault.copy(_placeholder = -1))

    val mediaSelectionGroupState = MediaSelectionGroupState(
        defaultMediaPreferenceState = defaultMediaPreferenceState,
        mediaSelectorSettingsState = mediaSelectorSettingsState,
        videoResolverSettingsState = videoResolverSettingsState,
    )

    val debugSettingsState = settingsRepository.debugSettings.stateInBackground(DebugSettings(_placeHolder = -1))
    val isInDebugMode by derivedStateOf {
        debugSettingsState.value.enabled
    }

    private val proxyTester = ProxyTester(
        clientProvider = clientProvider,
        flowScope = backgroundScope,
    )

    private val configureProxyUiState = combine(
        settingsRepository.proxySettings.flow,
        proxyProvider.proxy,
        proxyTester.testRunning,
        proxyTester.testResult,
    ) { settings, proxy, running, result ->
        ConfigureProxyUIState(
            config = settings.toUIConfig(),
            systemProxy = if (settings.default.mode == ProxyMode.SYSTEM && proxy != null) {
                SystemProxyPresentation.Detected(proxy)
            } else {
                SystemProxyPresentation.NotDetected
            },
            testState = ProxyTestState(
                testRunning = running,
                items = result.idToStateMap.toUIState(),
            ),
        )
    }
        .stateInBackground(
            ConfigureProxyUIState.Placeholder,
            SharingStarted.WhileSubscribed(),
        )

    val configureProxyState = ConfigureProxyState(
        state = configureProxyUiState,
        onUpdateConfig = { newConfig ->
            launchInBackground {
                settingsRepository.proxySettings.update { newConfig.toDataSettings() }
            }
        },
        onRequestReTest = { proxyTester.restartTest() },
    )

    val sourcePluginStoreState = SourcePluginStoreState(
        repositoryClient = sourcePluginRepositoryClient,
        registry = sourcePluginRegistry,
        repositoryCache = getKoin().get<Context>().dataStores.sourcePluginRepositoryCacheStore,
        scope = backgroundScope,
    )

    val debugTriggerState = DebugTriggerState(debugSettingsState, backgroundScope)
    val aboutTabInfo = AboutTabInfo(currentWynimeBuildConfig.versionName)

    val selfInfoFlow = SelfInfoStateProducer(koin = getKoin()).flow

    suspend fun startProxyTesterLoop() {
        loopTasker.invoke {
            launch { proxyTester.testRunnerLoop() }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private suspend fun serializeSettingsBackup(): String {
        val backup = SettingsBackup(
            mediaSelectorSettings = settingsRepository.mediaSelectorSettings.flow.first(),
            defaultMediaPreference = settingsRepository.defaultMediaPreference.flow.first(),
            profileSettings = settingsRepository.profileSettings.flow.first(),
            proxySettings = settingsRepository.proxySettings.flow.first(),
            mediaCacheSettings = settingsRepository.mediaCacheSettings.flow.first(),
            uiSettings = settingsRepository.uiSettings.flow.first(),
            themeSettings = settingsRepository.themeSettings.flow.first(),
            updateSettings = settingsRepository.updateSettings.flow.first(),
            videoScaffoldConfig = settingsRepository.videoScaffoldConfig.flow.first(),
            playerKernelConfig = settingsRepository.playerKernelConfig.flow.first(),
            videoResolverSettings = settingsRepository.videoResolverSettings.flow.first(),
            oneshotActionConfig = settingsRepository.oneshotActionConfig.flow.first(),
            analyticsSettings = settingsRepository.analyticsSettings.flow.first(),
            debugSettings = settingsRepository.debugSettings.flow.first(),
            tokenStore = tokenRepository.getTokenSaveSnapshot(),
        )

        return json.encodeToString(SettingsBackup.serializer(), backup)
    }

    @Suppress("DuplicatedCode")
    private suspend fun restoreSettingsBackup(content: String): Boolean {
        val backup = json.decodeFromString(SettingsBackup.serializer(), content)

        backup.mediaSelectorSettings?.let { settingsRepository.mediaSelectorSettings.set(it) }
        backup.defaultMediaPreference?.let { settingsRepository.defaultMediaPreference.set(it) }
        backup.profileSettings?.let { settingsRepository.profileSettings.set(it) }
        backup.proxySettings?.let { settingsRepository.proxySettings.set(it) }
        backup.mediaCacheSettings?.let { settingsRepository.mediaCacheSettings.set(it) }
        backup.uiSettings?.let { settingsRepository.uiSettings.set(it) }
        backup.themeSettings?.let { settingsRepository.themeSettings.set(it) }
        backup.updateSettings?.let { settingsRepository.updateSettings.set(it) }
        backup.videoScaffoldConfig?.let { settingsRepository.videoScaffoldConfig.set(it) }
        backup.playerKernelConfig?.let { settingsRepository.playerKernelConfig.set(it) }
        backup.videoResolverSettings?.let { settingsRepository.videoResolverSettings.set(it) }
        backup.oneshotActionConfig?.let { settingsRepository.oneshotActionConfig.set(it) }
        backup.analyticsSettings?.let { settingsRepository.analyticsSettings.set(it) }
        backup.debugSettings?.let { settingsRepository.debugSettings.set(it) }
        backup.tokenStore?.let { tokenRepository.restoreFromTokenSave(it) }

        return true
    }
}

private fun Map<String, ServiceConnectionTester.TestState>.toUIState(): List<ProxyTestItem> {
    return buildList {
        this@toUIState.forEach { (id, state) ->
            val case = when (id) {
                ServiceConnectionTesters.ID_BANGUMI -> ProxyTestCase.BangumiApi
                ServiceConnectionTesters.ID_BANGUMI_NEXT -> ProxyTestCase.BangumiNextApi
                else -> return@forEach
            }
            val result = when (state) {
                is ServiceConnectionTester.TestState.Idle -> ProxyTestCaseState.INIT
                is ServiceConnectionTester.TestState.Testing -> ProxyTestCaseState.RUNNING
                is ServiceConnectionTester.TestState.Success -> ProxyTestCaseState.SUCCESS
                is ServiceConnectionTester.TestState.Failed -> ProxyTestCaseState.FAILED
                is ServiceConnectionTester.TestState.Error -> ProxyTestCaseState.FAILED
            }
            add(ProxyTestItem(case, result))
        }
    }
}

@Serializable
private data class SettingsBackup(
    val mediaSelectorSettings: MediaSelectorSettings?,
    val defaultMediaPreference: MediaPreference?,
    val profileSettings: ProfileSettings?,
    val proxySettings: ProxySettings?,
    val mediaCacheSettings: MediaCacheSettings?,
    val uiSettings: UISettings?,
    val themeSettings: ThemeSettings?,
    val updateSettings: UpdateSettings?,
    val videoScaffoldConfig: VideoScaffoldConfig?,
    val playerKernelConfig: PlayerKernelConfig? = null,
    val videoResolverSettings: VideoResolverSettings?,
    val oneshotActionConfig: OneshotActionConfig?,
    val analyticsSettings: AnalyticsSettings?,
    val debugSettings: DebugSettings?,
    val tokenStore: TokenSave?
)
