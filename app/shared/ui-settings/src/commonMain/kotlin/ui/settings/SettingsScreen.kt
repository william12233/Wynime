package com.wynime.app.ui.settings

import kotlinx.serialization.SerialName

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.wynime.app.domain.session.auth.OAuthPlatform
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.navigation.rememberAsyncBrowserNavigator
import com.wynime.app.ui.adaptive.WynimeListDetailPaneScaffold
import com.wynime.app.ui.adaptive.WynimeTopAppBar
import com.wynime.app.ui.adaptive.WynimeTopAppBarDefaults
import com.wynime.app.ui.adaptive.ListDetailLayoutParameters
import com.wynime.app.ui.adaptive.PaneScope
import com.wynime.app.ui.adaptive.TopAppBarSize
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.animation.NavigationMotionScheme
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastExpanded
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.paneVerticalPadding
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.LocalAppChromeHazeState
import com.wynime.app.ui.foundation.theme.appChromeHazeSource
import com.wynime.app.ui.foundation.theme.isAppChromeFrostedGlassActive
import com.wynime.app.ui.foundation.widgets.BackNavigationIconButton
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.acknowledgements
import com.wynime.app.ui.lang.settings_about_build_info
import com.wynime.app.ui.lang.settings
import com.wynime.app.ui.lang.settings_acknowledgements_oss_licenses
import com.wynime.app.ui.lang.settings_category_app_ui
import com.wynime.app.ui.lang.settings_category_data_playback
import com.wynime.app.ui.lang.settings_category_network_storage
import com.wynime.app.ui.lang.settings_category_others
import com.wynime.app.ui.lang.settings_debug_dev_builds
import com.wynime.app.ui.lang.settings_debug_mode_enabled
import com.wynime.app.ui.lang.settings_tab_about
import com.wynime.app.ui.lang.settings_tab_account
import com.wynime.app.ui.lang.settings_tab_appearance
import com.wynime.app.ui.lang.settings_tab_debug
import com.wynime.app.ui.lang.settings_tab_log
import com.wynime.app.ui.lang.settings_tab_media_selector
import com.wynime.app.ui.lang.settings_tab_media_source
import com.wynime.app.ui.lang.settings_tab_player
import com.wynime.app.ui.lang.settings_tab_proxy
import com.wynime.app.ui.lang.settings_tab_settings_backup
import com.wynime.app.ui.lang.settings_tab_storage
import com.wynime.app.ui.lang.settings_tab_theme
import com.wynime.app.ui.lang.settings_tab_update
import com.wynime.app.ui.lang.settings_account_tracking_sync_title
import com.wynime.app.ui.settings.account.ProfileGroup
import com.wynime.app.ui.settings.account.BangumiTrackingSyncScreen
import com.wynime.app.ui.settings.account.SelfInfoBanner
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.tabs.WynimeHelperDestination
import com.wynime.app.ui.settings.tabs.DebugTab
import com.wynime.app.ui.settings.tabs.about.AboutTab
import com.wynime.app.ui.settings.tabs.about.AcknowledgementsTab
import com.wynime.app.ui.settings.tabs.about.BuildInfo
import com.wynime.app.ui.settings.tabs.about.BuildInfoTab
import com.wynime.app.ui.settings.tabs.about.OpenSourceLibrariesTab
import com.wynime.app.ui.settings.tabs.app.AppearanceGroup
import com.wynime.app.ui.settings.tabs.app.PlayerGroup
import com.wynime.app.ui.settings.tabs.app.SoftwareUpdateGroup
import com.wynime.app.ui.settings.tabs.log.LogTab
import com.wynime.app.ui.settings.tabs.media.BackupSettings
import com.wynime.app.ui.settings.tabs.media.CacheDirectoryGroup
import com.wynime.app.ui.settings.tabs.media.MediaSelectionGroup
import com.wynime.app.ui.settings.tabs.media.source.SourcePluginStoreTab
import com.wynime.app.ui.settings.tabs.network.ConfigureProxyGroup
import com.wynime.app.ui.settings.tabs.theme.ThemeGroup
import com.wynime.app.ui.update.devbuild.DevBuildsTab
import com.wynime.utils.platform.hasScrollingBug
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

typealias SettingsTab = com.wynime.app.navigation.SettingsTab

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onNavigateToBangumiLogin: () -> Unit,
    onNavigateToOAuth: (OAuthPlatform) -> Unit,
    loadOpenSourceLibrariesJsons: suspend () -> List<ByteArray>,
    modifier: Modifier = Modifier,
    initialTab: SettingsTab? = null,
    windowInsets: WindowInsets = WynimeWindowInsets.forColumnPageContent(),
    navigationIcon: @Composable () -> Unit = {},

) {
    val navigator: ThreePaneScaffoldNavigator<Nothing?> = rememberListDetailPaneScaffoldNavigator(
        initialDestinationHistory = buildList {
            add(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List))
            if (initialTab != null) {
                add(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail))
            }
        },
    )
    val layoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective)
    var lastSelectedTab by rememberSaveable(initialTab) {
        mutableStateOf(initialTab)
    }
    LaunchedEffect(Unit) {
        if (lastSelectedTab == null && !layoutParameters.preferSinglePane) {
            lastSelectedTab = SettingsTab.APPEARANCE
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    fun navigateToTab(tab: SettingsTab) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            lastSelectedTab = tab
        }
    }

    SettingsPageLayout(
        navigator,

        { lastSelectedTab },
        onSelectedTab = { tab ->
            navigateToTab(tab)
        },
        onClickBackOnListPage = {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateBack()
            }
        },
        onClickBackOnDetailPage = {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
            }
        },
        onNavigateToBangumiLogin = { onNavigateToOAuth(OAuthPlatform.BANGUMI) },
        navItems = {
            val selfInfoState by vm.selfInfoFlow.collectAsStateWithLifecycle()
            val bannerChecked by remember {
                derivedStateOf {
                    lastSelectedTab == SettingsTab.PROFILE
                }
            }
            SelfInfoBanner(
                selfInfoState,
                checked = bannerChecked,
                { navigateToTab(SettingsTab.PROFILE) },
                onNavigateToBangumiLogin,
                Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )

            Title(stringResource(Lang.settings_category_app_ui))
            Item(SettingsTab.APPEARANCE)
            Item(SettingsTab.THEME)

            Title(stringResource(Lang.settings_category_data_playback))
            Item(SettingsTab.PLAYER)
            Item(SettingsTab.MEDIA_SOURCE)
            Item(SettingsTab.MEDIA_SELECTOR)

            Title(stringResource(Lang.settings_category_network_storage))
            Item(SettingsTab.PROXY)

            Item(SettingsTab.STORAGE)

            Title(stringResource(Lang.settings_category_others))
            Item(SettingsTab.UPDATE)
            Item(SettingsTab.LOG)
            Item(SettingsTab.ABOUT)
            if (vm.isInDebugMode) {
                Item(SettingsTab.DEBUG)
            }
            Item(SettingsTab.SETTINGS_BACKUP)
        },
        tabContent = { currentTab ->
            val tabModifier = Modifier
            val toaster = LocalToaster.current
            val scope = rememberCoroutineScope()

            Column {
                when (currentTab) {
                    SettingsTab.ABOUT -> AboutTab(
                        vm.aboutTabInfo,
                        {
                            scope.launch {
                                if (vm.debugTriggerState.triggerDebugMode()) {
                                    toaster.toast(getString(Lang.settings_debug_mode_enabled))
                                }
                            }
                        },
                        onClickBuildInfo = {
                            navigateTo(DetailPaneRoutes.BuildInfo)
                        },
                        onClickFeedback = { browserNavigator.openBrowser(context, WynimeHelperDestination.ISSUE_TRACKER) },
                        onClickSource = { browserNavigator.openBrowser(context, WynimeHelperDestination.GITHUB_HOME) },
                        onClickAcknowledgements = {
                            navigateTo(DetailPaneRoutes.Acknowledgements)
                        },
                        modifier = tabModifier,
                    )

                    SettingsTab.LOG -> LogTab(
                        onClickFeedback = { browserNavigator.openBrowser(context, WynimeHelperDestination.ISSUE_TRACKER) },
                    )

                    SettingsTab.DEBUG -> DebugTab(
                        vm.debugSettingsState,
                        tabModifier,
                        onNavigateToDevBuilds = {
                            navigateTo(DetailPaneRoutes.DevBuilds)
                        },
                    )

                    else -> SettingsTab(
                        tabModifier,
                    ) {
                        when (currentTab) {
                            SettingsTab.PROFILE -> ProfileGroup(
                                onNavigateToBangumiSync = {
                                    navigateTo(DetailPaneRoutes.BangumiSync)
                                },
                                onNavigateToOAuth = onNavigateToOAuth,
                            )

                            SettingsTab.APPEARANCE -> AppearanceGroup(vm.uiSettings)
                            SettingsTab.THEME -> ThemeGroup(vm.themeSettings)
                            SettingsTab.UPDATE -> SoftwareUpdateGroup(vm.softwareUpdateGroupState)
                            SettingsTab.PLAYER -> {
                                PlayerGroup(
                                    vm.videoScaffoldConfig,
                                    vm.playerKernelConfig,
                                    vm.isInDebugMode,
                                )
                            }

                            SettingsTab.MEDIA_SOURCE -> {
                                SourcePluginStoreTab(vm.sourcePluginStoreState)
                            }

                            SettingsTab.MEDIA_SELECTOR -> MediaSelectionGroup(vm.mediaSelectionGroupState)
                            SettingsTab.PROXY -> ConfigureProxyGroup(
                                state = vm.configureProxyState,
                                onStartProxyTestLoop = { vm.startProxyTesterLoop() },
                            )

                            SettingsTab.STORAGE -> CacheDirectoryGroup(vm.cacheDirectoryGroupState)
                            SettingsTab.SETTINGS_BACKUP -> BackupSettings(vm.cacheDirectoryGroupState)
                            SettingsTab.ABOUT -> {}
                            SettingsTab.DEBUG -> {}
                            SettingsTab.LOG -> {}
                            null -> {}
                        }
                    }
                }
                Spacer(
                    Modifier.height(
                        currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding,
                    ),
                )
            }
        },
        modifier = modifier,
        contentWindowInsets = windowInsets,
        navigationIcon = navigationIcon,
        layoutParameters = layoutParameters,
        loadOpenSourceLibrariesJsons = loadOpenSourceLibrariesJsons,
    )
}

@Composable
internal fun SettingsPageLayout(
    navigator: ThreePaneScaffoldNavigator<Nothing?>,
    currentTab: () -> SettingsTab?,
    onSelectedTab: (SettingsTab) -> Unit,
    onClickBackOnListPage: () -> Unit,
    onClickBackOnDetailPage: () -> Unit,
    onNavigateToBangumiLogin: () -> Unit = {},
    navItems: @Composable (SettingsDrawerScope.() -> Unit),
    tabContent: @Composable SettingsDetailPaneScope.(currentTab: SettingsTab?) -> Unit,
    detailPaneBottomBar: @Composable BoxScope.(currentTab: SettingsTab?, windowInsets: WindowInsets) -> Unit =
        { _, _ -> },
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WynimeWindowInsets.forColumnPageContent(),
    containerColor: Color = WynimeThemeDefaults.pageContentBackgroundColor,
    layoutParameters: ListDetailLayoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective),
    navigationIcon: @Composable () -> Unit = {},
    loadOpenSourceLibrariesJsons: suspend () -> List<ByteArray>,
) = SettingsPageSurface(containerColor) {
    val layoutParametersState by rememberUpdatedState(layoutParameters)

    @Stable
    fun SettingsTab?.orDefault(): SettingsTab? {
        return if (layoutParametersState.preferSinglePane) {

            this
        } else {

            this ?: SettingsTab.Default
        }
    }

    val frostedGlassActive = isAppChromeFrostedGlassActive()

    val listPaneTopAppBarScrollBehavior = if (LocalPlatform.current.hasScrollingBug()) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }

    val detailPaneTopAppBarScrollBehavior = if (LocalPlatform.current.hasScrollingBug()) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior()
    }

    val listPaneScrollState = rememberScrollState()
    val topAppBarSize = if (LocalPlatform.current.hasScrollingBug()) {
        TopAppBarSize.SMALL
    } else {
        val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
        when {
            windowSizeClass.isHeightAtLeastExpanded -> TopAppBarSize.LARGE
            windowSizeClass.isHeightAtLeastMedium -> TopAppBarSize.MEDIUM
            else -> TopAppBarSize.SMALL
        }
    }
    val listPaneTopAppBar: @Composable PaneScope.() -> Unit = {
        WynimeTopAppBar(
            title = { WynimeTopAppBarDefaults.Title(stringResource(Lang.settings)) },
            navigationIcon = {
                if (navigator.canNavigateBack()) {
                    BackNavigationIconButton(
                        onNavigateBack = {
                            onClickBackOnListPage()
                        },
                    )
                } else {
                    navigationIcon()
                }
            },
            colors = if (isSinglePane) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    scrolledContainerColor = containerColor,
                )
            },
            scrollBehavior = listPaneTopAppBarScrollBehavior,
            windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            size = topAppBarSize,
        )
    }
    WynimeListDetailPaneScaffold(
        navigator,

        listPaneTopAppBar = if (frostedGlassActive) null else listPaneTopAppBar,
        listPaneContent = paneScope@{
            var listTopAppBarHeight by remember { mutableStateOf(0) }
            val drawerSheet: @Composable PaneScope.() -> Unit = {
                PermanentDrawerSheet(
                    Modifier
                        .paneContentPadding(extraStart = (-8).dp, extraEnd = (-8).dp)
                        .paneWindowInsetsPadding()
                        .fillMaxWidth()
                        .nestedScroll(listPaneTopAppBarScrollBehavior.nestedScrollConnection)
                        .verticalScroll(listPaneScrollState),
                    drawerContainerColor = Color.Unspecified,
                ) {
                    val highlightSelectedItemState = rememberUpdatedState(layoutParametersState.highlightSelectedItem)
                    val scope = remember(this, navigator, currentTab, highlightSelectedItemState) {
                        object : SettingsDrawerScope(), ColumnScope by this {
                            @Composable
                            override fun Item(item: SettingsTab) {
                                NavigationDrawerItem(
                                    icon = { Icon(getIcon(item), contentDescription = null) },
                                    label = { Text(getName(item)) },
                                    selected = item == currentTab() && highlightSelectedItemState.value,
                                    onClick = {
                                        onSelectedTab(item)
                                    },
                                )
                            }
                        }
                    }

                    val verticalPadding = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding

                    if (frostedGlassActive) {
                        Spacer(Modifier.height(with(LocalDensity.current) { listTopAppBarHeight.toDp() }))
                    }
                    Spacer(Modifier.height(verticalPadding - 8.dp))
                    navItems(scope)
                    Spacer(Modifier.height(verticalPadding))
                }
            }

            if (frostedGlassActive) {
                Box {
                    Box(
                        Modifier
                            .fillMaxSize()

                            .consumeWindowInsets(paneContentWindowInsets.only(WindowInsetsSides.Top))
                            .appChromeHazeSource(backgroundColor = containerColor),
                    ) {
                        drawerSheet()
                    }
                    Box(Modifier.onSizeChanged { listTopAppBarHeight = it.height }) {
                        listPaneTopAppBar()
                    }
                }
            } else {
                drawerSheet()
            }
        },

        detailPane = {
            AnimatedContent(
                currentTab(),
                Modifier.fillMaxSize(),
                transitionSpec = LocalWynimeMotionScheme.current.animatedContent.topLevel,
            ) { navigationTab ->
                val navMotionScheme = NavigationMotionScheme.current
                val topAppBarWindowInsets =
                    paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                val topAppBarColors = WynimeThemeDefaults.topAppBarColors(
                    containerColor = if (isSinglePane) {
                        containerColor
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                )
                val detailPaneBackStack = rememberSaveable(saver = DetailPaneBackStackSaver) {
                    mutableStateListOf<DetailPaneRoutes>(DetailPaneRoutes.Main)
                }

                val navigateUp: () -> Unit = {
                    if (detailPaneBackStack.size > 1) {
                        detailPaneBackStack.removeAt(detailPaneBackStack.lastIndex)
                    }
                }

                @Composable
                fun PaneScope.RouteContent(
                    scrollable: Boolean = true,
                    content: @Composable SettingsDetailPaneScope.() -> Unit,
                ) {
                    val paneScope = this
                    val scope = remember(paneScope, detailPaneBackStack) {
                        object : SettingsDetailPaneScope, PaneScope by paneScope {
                            override fun navigateTo(route: DetailPaneRoutes) {

                                if (detailPaneBackStack.lastOrNull() != route) {
                                    detailPaneBackStack.add(route)
                                }
                            }

                            override fun navigateUp() {
                                if (detailPaneBackStack.size > 1) {
                                    detailPaneBackStack.removeAt(detailPaneBackStack.lastIndex)
                                }
                            }
                        }
                    }
                    Column(
                        Modifier
                            .ifThen(scrollable) {
                                verticalScroll(rememberScrollState())
                            }
                            .padding(horizontal = SettingsScope.itemExtraHorizontalPadding)
                            .fillMaxWidth()
                            .wrapContentWidth()
                            .widthIn(max = 1000.dp),
                    ) {

                        val topAppBarUnderlapHeight = LocalSettingsTopAppBarUnderlapHeight.current
                        if (topAppBarUnderlapHeight > 0) {
                            Spacer(Modifier.height(with(LocalDensity.current) { topAppBarUnderlapHeight.toDp() }))
                        }

                        scope.content()

                        Spacer(
                            Modifier.windowInsetsBottomHeight(
                                WynimeWindowInsets.safeDrawing,
                            ),
                        )
                    }
                }

                NavDisplay(
                    backStack = detailPaneBackStack,
                    onBack = navigateUp,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    transitionSpec = {
                        navMotionScheme.enterTransition togetherWith navMotionScheme.exitTransition
                    },
                    popTransitionSpec = {
                        navMotionScheme.popEnterTransition togetherWith navMotionScheme.popExitTransition
                    },
                    predictivePopTransitionSpec = {
                        navMotionScheme.popEnterTransition togetherWith navMotionScheme.popExitTransition
                    },
                    entryProvider = entryProvider {
                    entry<DetailPaneRoutes.Main> {
                        val tab = navigationTab.orDefault()
                        DetailPaneRoute(
                            topAppBar = {
                                tab?.let {
                                    WynimeTopAppBar(
                                        title = {
                                            WynimeTopAppBarDefaults.Title(getName(it))
                                        },
                                        navigationIcon = {
                                            if (listDetailLayoutParameters.preferSinglePane) {
                                                BackNavigationIconButton(onClickBackOnDetailPage)
                                            }
                                        },
                                        colors = topAppBarColors,
                                        windowInsets = topAppBarWindowInsets,
                                        size = topAppBarSize,
                                        scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                    )
                                }
                            },
                            detailPaneTopAppBarScrollBehavior,
                            tabContent = {
                                RouteContent {
                                    tabContent(tab)
                                }
                            },
                            floatingContent = {
                                detailPaneBottomBar(
                                    tab,
                                    paneContentWindowInsets.only(
                                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                                    ),
                                )
                            },
                        )
                    }
                    entry<DetailPaneRoutes.Acknowledgements> {
                        DetailPaneRoute(
                            topAppBar = {
                                WynimeTopAppBar(
                                    title = { WynimeTopAppBarDefaults.Title(stringResource(Lang.acknowledgements)) },
                                    navigationIcon = {
                                        BackNavigationIconButton(navigateUp)
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {
                            RouteContent {
                                AcknowledgementsTab(
                                    onClickOpenSourceLicenses = {
                                        navigateTo(DetailPaneRoutes.OpenSourceLicenses)
                                    },
                                    Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    entry<DetailPaneRoutes.OpenSourceLicenses> {
                        DetailPaneRoute(
                            topAppBar = {
                                WynimeTopAppBar(
                                    title = {
                                        WynimeTopAppBarDefaults.Title(
                                            stringResource(Lang.settings_acknowledgements_oss_licenses),
                                        )
                                    },
                                    navigationIcon = {
                                        BackNavigationIconButton(navigateUp)
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {

                            RouteContent(scrollable = false) {
                                OpenSourceLibrariesTab(
                                    loadOpenSourceLibrariesJsons,
                                    Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    entry<DetailPaneRoutes.BuildInfo> {
                        DetailPaneRoute(
                            topAppBar = {
                                WynimeTopAppBar(
                                    title = { WynimeTopAppBarDefaults.Title(stringResource(Lang.settings_about_build_info)) },
                                    navigationIcon = {
                                        BackNavigationIconButton(navigateUp)
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {
                            RouteContent {
                                BuildInfoTab(remember { BuildInfo.current() }, Modifier.fillMaxSize())
                            }
                        }
                    }
                    entry<DetailPaneRoutes.DevBuilds> {
                        DetailPaneRoute(
                            topAppBar = {
                                WynimeTopAppBar(
                                    title = { WynimeTopAppBarDefaults.Title(stringResource(Lang.settings_debug_dev_builds)) },
                                    navigationIcon = {
                                        BackNavigationIconButton(navigateUp)
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {
                            RouteContent {
                                DevBuildsTab(Modifier.fillMaxSize())
                            }
                        }
                    }
                    entry<DetailPaneRoutes.BangumiSync> {
                        DetailPaneRoute(
                            topAppBar = {
                                WynimeTopAppBar(
                                    title = {
                                        WynimeTopAppBarDefaults.Title(
                                            stringResource(Lang.settings_account_tracking_sync_title),
                                        )
                                    },
                                    navigationIcon = {
                                        BackNavigationIconButton(navigateUp)
                                    },
                                    colors = topAppBarColors,
                                    windowInsets = topAppBarWindowInsets,
                                    size = topAppBarSize,
                                    scrollBehavior = detailPaneTopAppBarScrollBehavior,
                                )
                            },
                            detailPaneTopAppBarScrollBehavior,
                        ) {
                            RouteContent {
                                BangumiTrackingSyncScreen(
                                    onNavigateToLogin = onNavigateToBangumiLogin,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    },
                )
            }
        },
        modifier,
        layoutParameters = layoutParameters,
        contentWindowInsets = contentWindowInsets,
    )
}

@Composable
private fun SettingsPageSurface(containerColor: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppChromeHazeState provides rememberHazeState()) {
        Surface(color = containerColor, content = content)
    }
}

private val LocalSettingsTopAppBarUnderlapHeight = compositionLocalOf { 0 }

@Stable
interface SettingsDetailPaneScope : PaneScope {

    fun navigateTo(route: DetailPaneRoutes)

    fun navigateUp()
}

@Composable
private fun PaneScope.DetailPaneRoute(
    topAppBar: @Composable () -> Unit,
    detailPaneTopAppBarScrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    floatingContent: @Composable BoxScope.() -> Unit = {},
    tabContent: @Composable (PaneScope.() -> Unit),
) {
    if (isAppChromeFrostedGlassActive()) {

        var topAppBarHeight by remember { mutableStateOf(0) }
        Box(modifier) {
            Box(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(paneContentWindowInsets.only(WindowInsetsSides.Top))
                    .appChromeHazeSource(backgroundColor = WynimeThemeDefaults.pageContentBackgroundColor),
            ) {
                Column(
                    Modifier
                        .paneContentPadding(
                            extraStart = -SettingsScope.itemHorizontalPadding,
                            extraEnd = -SettingsScope.itemHorizontalPadding,
                        )
                        .paneWindowInsetsPadding()
                        .nestedScroll(detailPaneTopAppBarScrollBehavior.nestedScrollConnection),
                ) {
                    CompositionLocalProvider(LocalSettingsTopAppBarUnderlapHeight provides topAppBarHeight) {
                        tabContent()
                    }
                }
            }
            Box(Modifier.onSizeChanged { topAppBarHeight = it.height }) {
                topAppBar()
            }
            floatingContent()
        }
        return
    }

    Column(modifier) {
        topAppBar()

        Box(
            Modifier
                .fillMaxHeight()
                .consumeWindowInsets(paneContentWindowInsets.only(WindowInsetsSides.Top)),
        ) {
            Column(
                Modifier
                    .paneContentPadding(
                        extraStart = -SettingsScope.itemHorizontalPadding,
                        extraEnd = -SettingsScope.itemHorizontalPadding,
                    )
                    .paneWindowInsetsPadding()
                    .nestedScroll(detailPaneTopAppBarScrollBehavior.nestedScrollConnection),
            ) {
                tabContent()
            }
            floatingContent()
        }
    }
}

@Serializable
sealed class DetailPaneRoutes : NavKey {
    @SerialName("me.him188.ani.app.ui.settings.DetailPaneRoutes.Main")
    @Serializable
    data object Main : DetailPaneRoutes()

    @SerialName("me.him188.ani.app.ui.settings.DetailPaneRoutes.Acknowledgements")
    @Serializable
    data object Acknowledgements : DetailPaneRoutes()

    @SerialName("me.him188.ani.app.ui.settings.DetailPaneRoutes.OpenSourceLicenses")
    @Serializable
    data object OpenSourceLicenses : DetailPaneRoutes()

    @SerialName("me.him188.ani.app.ui.settings.DetailPaneRoutes.BuildInfo")
    @Serializable
    data object BuildInfo : DetailPaneRoutes()

    @SerialName("me.him188.ani.app.ui.settings.DetailPaneRoutes.DevBuilds")
    @Serializable
    data object DevBuilds : DetailPaneRoutes()

    @SerialName("me.him188.ani.app.ui.settings.DetailPaneRoutes.BangumiSync")
    @Serializable
    data object BangumiSync : DetailPaneRoutes()
}

private val DetailPaneBackStackSaver: Saver<SnapshotStateList<DetailPaneRoutes>, Any> = listSaver(
    save = { stack -> stack.map { it::class.simpleName ?: "Main" } },
    restore = { saved ->

        if (saved.isEmpty()) {
            null
        } else {
            saved.map { name ->
                when (name as String) {
                    "Acknowledgements" -> DetailPaneRoutes.Acknowledgements
                    "OpenSourceLicenses" -> DetailPaneRoutes.OpenSourceLicenses
                    "BuildInfo" -> DetailPaneRoutes.BuildInfo
                    "DevBuilds" -> DetailPaneRoutes.DevBuilds
                    "BangumiSync" -> DetailPaneRoutes.BangumiSync
                    else -> DetailPaneRoutes.Main
                }
            }.toMutableStateList()
        }
    },
)

@Stable
abstract class SettingsDrawerScope internal constructor() : ColumnScope {
    @Composable
    abstract fun Item(item: SettingsTab)

    @Composable
    fun Title(text: String, paddingTop: Dp = 20.dp) {
        Text(
            text,
            Modifier
                .padding(horizontal = 16.dp)
                .padding(top = paddingTop, bottom = 12.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Stable
private fun getIcon(tab: SettingsTab): ImageVector {
    return when (tab) {
        SettingsTab.PROFILE -> Icons.Outlined.AccountCircle
        SettingsTab.APPEARANCE -> Icons.Outlined.SettingsApplications
        SettingsTab.THEME -> Icons.Outlined.Palette
        SettingsTab.UPDATE -> Icons.Outlined.Update
        SettingsTab.PLAYER -> Icons.Outlined.SmartDisplay
        SettingsTab.MEDIA_SOURCE -> Icons.Outlined.Subscriptions
        SettingsTab.MEDIA_SELECTOR -> Icons.Outlined.FilterList
        SettingsTab.PROXY -> Icons.Outlined.VpnKey

        SettingsTab.STORAGE -> Icons.Outlined.Storage
        SettingsTab.SETTINGS_BACKUP -> Icons.Outlined.Settings
        SettingsTab.ABOUT -> Icons.Outlined.Info
        SettingsTab.LOG -> Icons.Outlined.Feedback
        SettingsTab.DEBUG -> Icons.Outlined.Science
    }
}

@Stable
@Composable
private fun getName(tab: SettingsTab): String {
    return when (tab) {
        SettingsTab.PROFILE -> stringResource(Lang.settings_tab_account)
        SettingsTab.APPEARANCE -> stringResource(Lang.settings_tab_appearance)
        SettingsTab.THEME -> stringResource(Lang.settings_tab_theme)
        SettingsTab.PLAYER -> stringResource(Lang.settings_tab_player)
        SettingsTab.MEDIA_SOURCE -> stringResource(Lang.settings_tab_media_source)
        SettingsTab.MEDIA_SELECTOR -> stringResource(Lang.settings_tab_media_selector)
        SettingsTab.PROXY -> stringResource(Lang.settings_tab_proxy)

        SettingsTab.STORAGE -> stringResource(Lang.settings_tab_storage)
        SettingsTab.SETTINGS_BACKUP -> stringResource(Lang.settings_tab_settings_backup)
        SettingsTab.LOG -> stringResource(Lang.settings_tab_log)
        SettingsTab.UPDATE -> stringResource(Lang.settings_tab_update)
        SettingsTab.ABOUT -> stringResource(Lang.settings_tab_about)
        SettingsTab.DEBUG -> stringResource(Lang.settings_tab_debug)
    }
}

@Composable
fun SettingsTab(
    modifier: Modifier = Modifier,
    content: @Composable SettingsScope.() -> Unit,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsScope.itemVerticalSpacing),
    ) {
        val scope = remember(this) {
            object : SettingsScope(), ColumnScope by this@Column {}
        }
        scope.content()
    }
}
