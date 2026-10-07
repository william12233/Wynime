package com.wynime.app.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import com.wynime.app.domain.foundation.VersionExpiryService
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.navigation.MainScreenPage
import com.wynime.app.navigation.SettingsTab
import com.wynime.app.navigation.getIcon
import com.wynime.app.navigation.getText
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.WynimeBrand
import com.wynime.app.ui.adaptive.navigation.WynimeNavigationSuite
import com.wynime.app.ui.adaptive.navigation.WynimeNavigationSuiteDefaults
import com.wynime.app.ui.adaptive.navigation.WynimeNavigationSuiteLayout
import com.wynime.app.ui.exploration.ExplorationPageViewModel
import com.wynime.app.ui.download.DownloadManagementScreen
import com.wynime.app.ui.download.DownloadManagementViewModel
import com.wynime.app.ui.download.createDownloadManagementViewModel
import com.wynime.app.ui.exploration.ExplorationScreen
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.desktopCaptionButton
import com.wynime.app.ui.foundation.layout.desktopTitleBar
import com.wynime.app.ui.foundation.layout.desktopTitleBarPadding
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isTopRight
import com.wynime.app.ui.foundation.layout.setRequestFullScreen
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.LocalAppChromeHazeState
import com.wynime.app.ui.foundation.theme.LocalAppChromeOverlayInsets
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.showLoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_search
import com.wynime.app.ui.lang.settings
import com.wynime.app.ui.lang.settings_update_version_expired_copied_to_clipboard
import com.wynime.app.ui.lang.settings_update_version_expired_export_settings
import com.wynime.app.ui.lang.settings_update_version_expired_import_settings_hint
import com.wynime.app.ui.lang.settings_update_version_expired_message
import com.wynime.app.ui.lang.settings_update_version_expired_message_with_latest
import com.wynime.app.ui.lang.settings_update_version_expired_title
import com.wynime.app.ui.settings.SettingsViewModel
import com.wynime.app.ui.settings.account.ProfilePopup
import com.wynime.app.ui.settings.account.ProfileViewModel
import com.wynime.app.ui.subject.collection.CollectionPage
import com.wynime.app.ui.subject.collection.UserCollectionsViewModel
import com.wynime.app.ui.update.AppUpdateViewModel
import com.wynime.app.ui.update.UpdateNotifier
import com.wynime.app.ui.user.SelfInfoUiState
import com.wynime.utils.platform.isAndroid
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
fun MainScreen(
    page: MainScreenPage,
    selfInfo: SelfInfoUiState,
    modifier: Modifier = Modifier,
    onNavigateToPage: (MainScreenPage) -> Unit,
    onNavigateToSettings: (tab: SettingsTab?) -> Unit,
    onNavigateToSearch: () -> Unit,
    navigationLayoutType: NavigationSuiteType = WynimeNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo1(),
    ),
) {
    if (LocalPlatform.current.isAndroid()) {
        val context = LocalContext.current
        val window = LocalPlatformWindow.current
        LaunchedEffect(true) {
            context.setRequestFullScreen(window, false)
        }
    }

    MainScreenContent(
        page,
        selfInfo,
        onNavigateToPage,
        onNavigateToSettings,
        onNavigateToSearch,
        modifier,
        navigationLayoutType,
    )
}

@Composable
private fun MainScreenContent(
    page: MainScreenPage,
    selfInfo: SelfInfoUiState,
    onNavigateToPage: (MainScreenPage) -> Unit,
    onNavigateToSettings: (tab: SettingsTab?) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
    navigationLayoutType: NavigationSuiteType = WynimeNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo1(),
    ),
) {
    val explorationPageViewModel = viewModel { ExplorationPageViewModel() }
    val userCollectionsViewModel = viewModel<UserCollectionsViewModel> { UserCollectionsViewModel() }
    val downloadManagementViewModel = viewModel { createDownloadManagementViewModel() }

    var showAccountSettingsPopup: Boolean by remember { mutableStateOf(false) }
    val profileViewModel = viewModel { ProfileViewModel() }

    val navigatorState = rememberUpdatedState(LocalNavigator.current)
    val navigator by navigatorState

    CompositionLocalProvider(LocalAppChromeHazeState provides rememberHazeState()) {
        MainScreenNavigationLayout(
            page = page,
            selfInfo = selfInfo,
            onNavigateToPage = onNavigateToPage,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToSearch = onNavigateToSearch,
            onLogin = { showAccountSettingsPopup = true },
            explorationPageViewModel = explorationPageViewModel,
            userCollectionsViewModel = userCollectionsViewModel,
            downloadManagementViewModel = downloadManagementViewModel,
            modifier = modifier,
            navigationLayoutType = navigationLayoutType,
        )
    }

    if (showAccountSettingsPopup) {
        ProfilePopup(
            vm = profileViewModel,
            onDismissRequest = { showAccountSettingsPopup = false },
            onNavigateToSettings = {
                showAccountSettingsPopup = false
                onNavigateToSettings(null)
            },
            onNavigateToAccountSettings = {
                showAccountSettingsPopup = false
                onNavigateToSettings(SettingsTab.PROFILE)
            },
            onNavigateToPlaybackHistory = {
                showAccountSettingsPopup = false
                navigator.navigatePlaybackHistory()
            },
            onNavigateToLogin = {
                showAccountSettingsPopup = false
                navigator.navigateBangumiAuthorize()
            },
        )
    }
}

@Composable
private fun MainScreenNavigationLayout(
    page: MainScreenPage,
    selfInfo: SelfInfoUiState,
    onNavigateToPage: (MainScreenPage) -> Unit,
    onNavigateToSettings: (tab: SettingsTab?) -> Unit,
    onNavigateToSearch: () -> Unit,
    onLogin: () -> Unit,
    explorationPageViewModel: ExplorationPageViewModel,
    userCollectionsViewModel: UserCollectionsViewModel,
    downloadManagementViewModel: DownloadManagementViewModel,
    modifier: Modifier = Modifier,
    navigationLayoutType: NavigationSuiteType = WynimeNavigationSuiteDefaults.calculateLayoutType(
        currentWindowAdaptiveInfo1(),
    ),
) {
    val scope = rememberCoroutineScope()
    val navigatorState = rememberUpdatedState(LocalNavigator.current)
    val navigator by navigatorState

    WynimeNavigationSuiteLayout(
        navigationSuite = {
            WynimeNavigationSuite(
                layoutType = navigationLayoutType,
                colors = NavigationSuiteDefaults.colors(
                    navigationDrawerContainerColor = WynimeThemeDefaults.navigationContainerColor,
                    navigationBarContainerColor = WynimeThemeDefaults.navigationContainerColor,
                    navigationRailContainerColor = WynimeThemeDefaults.navigationContainerColor,
                ),
                navigationRailHeader = {
                    FloatingActionButton(
                        onNavigateToSearch,
                        Modifier
                            .desktopTitleBarPadding()
                            .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium) {

                                padding(vertical = 48.dp)
                            },
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    ) {
                        Icon(Icons.Rounded.Search, stringResource(Lang.exploration_search))
                    }
                },
                navigationRailFooter = {
                    NavigationRailItem(
                        modifier = Modifier.padding(bottom = itemSpacing)
                            .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium) {

                                padding(vertical = 16.dp)
                            },
                        selected = false,
                        onClick = { onNavigateToSettings(null) },
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        enabled = true,
                        label = { Text(stringResource(Lang.settings)) },
                        alwaysShowLabel = true,
                        colors = itemColors,
                    )
                },
                navigationRailItemSpacing = 8.dp,
            ) {
                for (entry in MainScreenPage.visibleEntries) {
                    item(
                        page == entry,
                        onClick = { onNavigateToPage(entry) },
                        onDoubleClick = {
                            scope.launch {
                                when (entry) {
                                    MainScreenPage.Exploration ->
                                        explorationPageViewModel.explorationPageState.pageScrollState.animateScrollToItem(
                                            0,
                                        )

                                    MainScreenPage.Collection ->
                                        userCollectionsViewModel.state.scrollToTop()

                                    MainScreenPage.CacheManagement -> {

                                    }
                                }
                            }
                        },
                        icon = { Icon(entry.getIcon(), null) },
                        label = { Text(text = entry.getText()) },
                    )
                }
            }
        },
        modifier,
        layoutType = navigationLayoutType,
    ) {
        val coroutineScope = rememberCoroutineScope()

        val isRightCaptionButton = WindowInsets.desktopCaptionButton.isTopRight()
        val toaster = LocalToaster.current

        TabContent(
            layoutType = navigationLayoutType,
            selfInfo = selfInfo,
            modifier = Modifier.ifThen(navigationLayoutType != NavigationSuiteType.NavigationBar && !isRightCaptionButton) {

                consumeWindowInsets(WindowInsets.desktopTitleBar())
            },
        ) {
            val wynimeMotionScheme = LocalWynimeMotionScheme.current

            val pageWindowInsets = WynimeWindowInsets.forPageContent()
                .add(LocalAppChromeOverlayInsets.current)
            AnimatedContent(
                page,
                Modifier.fillMaxSize(),
                transitionSpec = {
                    wynimeMotionScheme.topLevelTransition
                },
            ) { page ->
                when (page) {
                    MainScreenPage.Exploration -> {
                        ExplorationScreen(
                            explorationPageViewModel.explorationPageState,
                            selfInfo,
                            onSearch = onNavigateToSearch,
                            onClickSettings = { navigator.navigateSettings() },
                            onClickLogin = onLogin,
                            modifier = Modifier.fillMaxSize(),
                            windowInsets = pageWindowInsets,
                        )
                    }

                    MainScreenPage.Collection -> {
                        CollectionPage(
                            state = userCollectionsViewModel.state,
                            selfInfo = selfInfo,
                            fullSyncState = userCollectionsViewModel.fullSyncState.collectAsStateWithLifecycle().value,
                            onClickSearch = onNavigateToSearch,
                            onClickLogin = onLogin,
                            onClickSettings = { navigator.navigateSettings() },
                            onFullSync = userCollectionsViewModel::fullSync,
                            onCollectionUpdate = { subjectId, episode ->
                                coroutineScope.launch {
                                    userCollectionsViewModel.toggleEpisodeCollection(
                                        subjectId,
                                        episode.episodeId,
                                        episode.collectionType,
                                    )?.let { toaster.showLoadError(it) }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            windowInsets = pageWindowInsets,
                            enableAnimation = userCollectionsViewModel.myCollectionsSettings.enableListAnimation1,
                        )
                    }

                    MainScreenPage.CacheManagement -> {
                        DownloadManagementScreen(
                            downloadManagementViewModel,
                            selfInfo = selfInfo,
                            onPlay = { navigator.navigateEpisodeDetails(it.subjectId, it.episodeId) },
                            onNavigateCacheDetail = { navigator.navigateCacheDetails(it) },
                            onClickLogin = onLogin,
                            modifier = Modifier.fillMaxSize(),
                            navigationIcon = { },
                            windowInsets = pageWindowInsets,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    layoutType: NavigationSuiteType,
    selfInfo: SelfInfoUiState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = when (layoutType) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.None -> RectangleShape

        NavigationSuiteType.NavigationRail,
        NavigationSuiteType.NavigationDrawer -> MaterialTheme.shapes.extraLarge.copy(
            topEnd = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )

        else -> RectangleShape
    }
    Surface(
        modifier.clip(shape),
        shape = shape,
        color = WynimeThemeDefaults.pageContentBackgroundColor,
    ) {
        Box(Modifier.fillMaxWidth()) {
            content()

            BottomNotifierStack(
                Modifier.matchParentSize()
                    .padding(LocalAppChromeOverlayInsets.current.asPaddingValues()),
                top = { UpdateNotifierWithVersionExpiryCheck() },
                bottom = {},
            )
        }
    }
}

@Composable
internal fun BottomNotifierStack(
    modifier: Modifier = Modifier,
    top: @Composable BoxScope.() -> Unit,
    bottom: @Composable BoxScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        Box(Modifier.fillMaxWidth().weight(1f, fill = false), content = top)
        Box(Modifier.fillMaxWidth(), content = bottom)
    }
}

@Composable
private fun BoxScope.UpdateNotifierWithVersionExpiryCheck() {

    val updateVm = viewModel { AppUpdateViewModel() }
    val versionExpiryService = remember { KoinPlatform.getKoin().get<VersionExpiryService>() }
    val expired by versionExpiryService.state.collectAsStateWithLifecycle(null)
    LaunchedEffect(expired) {
        if (expired != null) {
            updateVm.startCheckLatestVersion(null)
        }
    }
    if (expired != null) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(Lang.settings_update_version_expired_title),
                    Modifier.padding(all = 24.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )

                val latestVersion = expired?.latestVersion
                Text(
                    buildAnnotatedString {
                        append(
                            if (latestVersion != null) {
                                stringResource(
                                    Lang.settings_update_version_expired_message_with_latest,
                                    latestVersion,
                                )
                            } else {
                                stringResource(Lang.settings_update_version_expired_message)
                            },
                        )
                        pushLink(
                            LinkAnnotation.Url(
                                WynimeBrand.githubHome,
                                styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
                            ),
                        )
                        append(WynimeBrand.githubHome)
                    },
                    Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.titleMedium,
                )

                val settingsVm = viewModel<SettingsViewModel> { SettingsViewModel() }
                val asyncHandler = rememberAsyncHandler()
                val toaster = LocalToaster.current
                val clipboard = LocalClipboard.current

                Row(
                    Modifier.padding(horizontal = 24.dp).padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Lang.settings_update_version_expired_import_settings_hint),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    OutlinedButton(
                        {
                            asyncHandler.launch {
                                val data = settingsVm.cacheDirectoryGroupState.onGetBackupData()
                                clipboard.setClipEntryText(data)
                                toaster.toast(getString(Lang.settings_update_version_expired_copied_to_clipboard))
                            }
                        },
                    ) {
                        Text(stringResource(Lang.settings_update_version_expired_export_settings))
                    }
                }
            }
        }
    }
    UpdateNotifier(viewModel = updateVm)
}
