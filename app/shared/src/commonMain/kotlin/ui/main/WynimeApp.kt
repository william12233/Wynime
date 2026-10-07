package com.wynime.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import com.wynime.app.data.repository.subject.BangumiTrackingSyncRepository
import com.wynime.app.data.models.preference.EpisodeProgressSettings
import com.wynime.app.data.models.preference.SubjectAppearanceSettings
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.ScopedHttpClientUserAgent
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.mediasource.web.captcha.WebCaptchaDialogHost
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.domain.session.SessionState
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.navigation.BrowserNavigator
import com.wynime.app.navigation.MainScreenPage
import com.wynime.app.navigation.NavRoutes
import com.wynime.app.tools.LocalTimeFormatter
import com.wynime.app.tools.TimeFormatter
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.LocalPlatformFontFamily
import com.wynime.app.ui.foundation.LocalEpisodeProgressSettings
import com.wynime.app.ui.foundation.LocalSketch
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.input.ActiveInputSourceState
import com.wynime.app.ui.foundation.input.LocalActiveInputSource
import com.wynime.app.ui.foundation.input.trackActiveInputSource
import com.wynime.app.ui.foundation.interaction.clearFocusOnUnhandledTap
import com.wynime.app.ui.foundation.navigation.LocalBackDispatcher
import com.wynime.app.ui.foundation.navigation.onBackNavigationInput
import com.wynime.app.ui.foundation.rememberWynimeSketchInstance
import com.wynime.app.ui.foundation.rememberPlatformFontFamily
import com.wynime.app.ui.foundation.theme.WynimeTheme
import com.wynime.app.ui.foundation.theme.LocalThemeSettings
import com.wynime.app.ui.lang.LocaleZhCN
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.currentPlatform
import com.wynime.utils.platform.isMobile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class WynimeAppState(
    val initialNavRoute: NavRoutes,
    val mainSceneInitialPage: MainScreenPage,
    val themeSettings: ThemeSettings,
    val imageLoaderClient: ScopedHttpClient,
    val overlayComposables: List<@Composable () -> Unit>,
    val platformFont: String?,
    val episodeProgressSettings: EpisodeProgressSettings,
    val subjectAppearanceSettings: SubjectAppearanceSettings,
)

@Stable
class WynimeAppViewModel : AbstractViewModel(), KoinComponent {
    private val settings: SettingsRepository by inject()
    private val httpClientProvider: HttpClientProvider by inject()
    private val downloadManager: MediaDownloadManager by inject()
    private val webSessionManager: WebSessionManager by inject()
    private val userRepository: UserRepository by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val trackingSyncRepository: BangumiTrackingSyncRepository by inject()

    private val imageLoaderClient = httpClientProvider.get(ScopedHttpClientUserAgent.WYNIME)

    private val mediaCacheComposablesFlow = flowOf(
        downloadManager.storages.map { @Composable { it.engine.ComposeContent() } },
    )

    val browserNavigator by inject<BrowserNavigator>()

    val bangumiSessionExpired =
        combine(userRepository.selfInfoFlow, sessionStateProvider.stateFlow) { selfInfo, sessionState ->
            val isBound = selfInfo?.bangumiUsername?.isNotBlank() == true
            val serverTokenInvalid = selfInfo?.isBangumiSessionValid == false
            val localTokenMissing = sessionState is SessionState.Valid && !sessionState.bangumiConnected
            isBound && (serverTokenInvalid || localTokenMissing)
        }.distinctUntilChanged().stateInBackground(false)

    val appState: Flow<WynimeAppState?> = combine(
        settings.themeSettings.flow,
        settings.uiSettings.flow.take(1).map { it.mainSceneInitialPage },
        settings.uiSettings.flow,
        mediaCacheComposablesFlow,
    ) { themeSettings, mainSceneInitialPage, uiSettings, mediaCacheComposables ->
        WynimeAppState(
            NavRoutes.Main(mainSceneInitialPage),
            uiSettings.mainSceneInitialPage,
            themeSettings,
            imageLoaderClient,
            mediaCacheComposables + listOf(@Composable { WebCaptchaDialogHost(webSessionManager) }),

            if (currentPlatform() is Platform.Windows && uiSettings.appLanguage == LocaleZhCN) {
                "Microsoft YaHei UI"
            } else null,
            uiSettings.episodeProgress,
            uiSettings.subjectAppearance,
        )
    }.shareInBackground(
        started = SharingStarted.Eagerly,
        replay = 1,
    )

    suspend fun unbindBangumi() {
        userRepository.clearSelfInfo()
    }

    fun verifyPendingCollectionRemovals() {
        backgroundScope.launch { trackingSyncRepository.confirmPendingWebRemovals() }
    }

}

@Composable
fun WynimeApp(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val viewModel = viewModel { WynimeAppViewModel() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.verifyPendingCollectionRemovals()
    }

    val appState = viewModel.appState.collectAsStateWithLifecycle(null).value ?: return

    CompositionLocalProvider(
        LocalSketch provides rememberWynimeSketchInstance(appState.imageLoaderClient),
        LocalTimeFormatter provides remember { TimeFormatter() },
        LocalThemeSettings provides appState.themeSettings,
        LocalEpisodeProgressSettings provides appState.episodeProgressSettings,
        LocalSubjectAppearanceSettings provides appState.subjectAppearanceSettings,
        LocalPlatformFontFamily provides rememberPlatformFontFamily(appState.platformFont),
        LocalActiveInputSource provides remember { ActiveInputSourceState() },
    ) {
        val backDispatcher = LocalBackDispatcher.current

        WynimeTheme {
            Box(
                modifier = modifier
                    .trackActiveInputSource(LocalActiveInputSource.current)
                    .onBackNavigationInput(backDispatcher::onBackPressed)
                    .ifThen(LocalPlatform.current.isMobile()) {
                        clearFocusOnUnhandledTap()
                    },
            ) {
                Box {
                    for (composable in appState.overlayComposables) {
                        composable()
                    }
                }

                Column {
                    content()
                }
            }
        }
    }
}
