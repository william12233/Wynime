package com.wynime.app.ui.main

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.search.SubjectSearchQuery
import com.wynime.app.domain.session.auth.OAuthPlatform
import com.wynime.app.navigation.WynimeNavigator
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.navigation.MainScreenPage
import com.wynime.app.navigation.NavRoutes
import com.wynime.app.navigation.OverrideNavigation
import com.wynime.app.navigation.SubjectDetailPlaceholder
import com.wynime.app.navigation.rememberWynimeBackStack
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.navigation.LocalBrowserNavigator
import com.wynime.app.shared.loadOpenSourceLibrariesJsons
import com.wynime.app.ui.adaptive.navigation.WynimeNavigationSuiteDefaults
import com.wynime.app.ui.download.DownloadManagementScreen
import com.wynime.app.ui.download.createDownloadManagementViewModel
import com.wynime.app.ui.download.createSubjectDownloadsViewModel
import com.wynime.app.ui.download.details.MediaCacheDetailsPageViewModel
import com.wynime.app.ui.download.details.MediaCacheDetailsScreen
import com.wynime.app.ui.download.details.MediaDetails
import com.wynime.app.ui.download.details.MediaDetailsLazyGrid
import com.wynime.app.ui.download.subject.SubjectDownloadsScreen
import com.wynime.app.ui.exploration.schedule.ScheduleScreen
import com.wynime.app.ui.exploration.schedule.ScheduleViewModel
import com.wynime.app.ui.foundation.animation.NavigationMotionScheme
import com.wynime.app.ui.foundation.animation.ProvideWynimeMotionCompositionLocals
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.desktopTitleBar
import com.wynime.app.ui.foundation.widgets.BackNavigationIconButton
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.TopAppBarActionButton
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.main_network_check_failed
import com.wynime.app.ui.oauth.OAuthAuthorizeScreen
import com.wynime.app.ui.oauth.OAuthAuthorizeViewModel
import com.wynime.app.ui.playback.PlaybackHistoryScreen
import com.wynime.app.ui.playback.PlaybackHistorySyncStatusScreen
import com.wynime.app.ui.playback.PlaybackHistoryViewModel
import com.wynime.app.ui.profile.auth.WynimeContactList
import com.wynime.app.ui.search.SearchScreen
import com.wynime.app.ui.settings.SettingsScreen
import com.wynime.app.ui.settings.SettingsViewModel
import com.wynime.app.ui.subject.details.SubjectDetailsScreen
import com.wynime.app.ui.subject.details.SubjectDetailsViewModel
import com.wynime.app.ui.subject.episode.EpisodeScreen
import com.wynime.app.ui.subject.episode.EpisodeViewModel
import com.wynime.app.ui.subject.person.CharacterDetailsScreen
import com.wynime.app.ui.subject.person.CharacterDetailsViewModel
import com.wynime.app.ui.subject.person.PersonDetailsScreen
import com.wynime.app.ui.subject.person.PersonDetailsViewModel
import com.wynime.app.ui.subject.relations.SubjectRelationGraphScreen
import com.wynime.app.ui.subject.relations.SubjectRelationGraphViewModel
import com.wynime.app.ui.user.SelfInfoStateProducer
import org.jetbrains.compose.resources.stringResource

@Composable
fun WynimeAppContent(wynimeNavigator: WynimeNavigator) {
    val wynimeAppViewModel = viewModel<WynimeAppViewModel>()
    val appState = wynimeAppViewModel.appState.collectAsStateWithLifecycle(null).value ?: return

    val backStack = rememberWynimeBackStack(appState.initialNavRoute)
    wynimeNavigator.setBackStack(backStack)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(
            LocalNavigator provides wynimeNavigator,
            LocalBrowserNavigator providesDefault wynimeAppViewModel.browserNavigator,
        ) {
            ProvideWynimeMotionCompositionLocals {
                WynimeAppContentImpl(
                    wynimeNavigator,
                    backStack,
                    appState.mainSceneInitialPage,
                    Modifier.fillMaxSize(),
                )
                BangumiSessionExpiredPromptHost(
                    viewModel = wynimeAppViewModel,
                    enabled = appState.initialNavRoute is NavRoutes.Main,
                    onLogin = {
                        wynimeNavigator.navigateBangumiAuthorize()
                    },
                )
            }
        }
    }
}

@Composable
private fun WynimeAppContentImpl(
    wynimeNavigator: WynimeNavigator,
    backStack: List<NavRoutes>,
    mainSceneInitialPage: MainScreenPage,
    modifier: Modifier = Modifier,
) {

    val windowInsetsWithoutTitleBar = ScaffoldDefaults.contentWindowInsets
    val windowInsets = ScaffoldDefaults.contentWindowInsets
        .add(WindowInsets.desktopTitleBar())
    val navMotionScheme by rememberUpdatedState(NavigationMotionScheme.current)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { wynimeNavigator.popBackStack() },
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
            entry<NavRoutes.EmailLoginStart> { route ->
                LaunchedEffect(route) {
                    wynimeNavigator.popBackStack(route, inclusive = true)
                    wynimeNavigator.navigateBangumiAuthorize()
                }
            }
            entry<NavRoutes.EmailLoginVerify> { route ->
                LaunchedEffect(route) {
                    wynimeNavigator.popBackStack(route, inclusive = true)
                    wynimeNavigator.navigateBangumiAuthorize()
                }
            }
            entry<NavRoutes.OAuthAuthorize> { route ->
                val platform = OAuthPlatform.BANGUMI
                val vm = viewModel<OAuthAuthorizeViewModel>(key = platform.id) { OAuthAuthorizeViewModel(platform) }
                OAuthAuthorizeScreen(
                    vm,
                    onNavigateBack = {
                        wynimeNavigator.popBackStack(route, true)
                    },
                    onNavigateSettings = {
                        wynimeNavigator.navigateSettings()
                    },
                    contactActions = {
                        WynimeContactList()
                    },
                    onAuthorizeSuccess = {
                        wynimeNavigator.popBackStack(route, true)
                        wynimeNavigator.popBackStack(NavRoutes.EmailLoginVerify, true)
                        wynimeNavigator.popBackStack(NavRoutes.EmailLoginStart, true)
                    },
                )
            }
            entry<NavRoutes.QrLoginScan> { route ->
                LaunchedEffect(route) {
                    wynimeNavigator.popBackStack(route, inclusive = true)
                    wynimeNavigator.navigateBangumiAuthorize()
                }
            }
            entry<NavRoutes.QrLoginConfirm> { route ->
                LaunchedEffect(route) {
                    wynimeNavigator.popBackStack(route, inclusive = true)
                    wynimeNavigator.navigateBangumiAuthorize()
                }
            }
            entry<NavRoutes.BangumiMerge> { route ->
                LaunchedEffect(route) {
                    wynimeNavigator.popBackStack(route, inclusive = true)
                    wynimeNavigator.navigateSettings()
                }
            }
            entry<NavRoutes.Main> { route ->
                val navigationLayoutType =
                    WynimeNavigationSuiteDefaults.calculateLayoutType(
                        currentWindowAdaptiveInfo1(),
                    )

                val vm = viewModel { MainScreenSharedViewModel() }
                var currentPage by rememberSaveable { mutableStateOf(route.initialPage) }

                val toaster = LocalToaster.current
                val networkCheckFailedMessage = stringResource(Lang.main_network_check_failed)
                LaunchedEffect(vm) {
                    vm.networkCheckFailed.collect {
                        toaster.toast(networkCheckFailedMessage)
                    }
                }

                OverrideNavigation(
                    {
                        object : WynimeNavigator by it {
                            override fun navigateMain(page: MainScreenPage, popUpTargetInclusive: NavRoutes?) {
                                currentPage = page
                            }
                        }
                    },
                ) {
                    val selfInfo by vm.selfInfo.collectAsState()
                    MainScreen(
                        page = currentPage,
                        selfInfo = selfInfo,
                        onNavigateToPage = { currentPage = it },
                        onNavigateToSettings = { wynimeNavigator.navigateSettings(it) },
                        onNavigateToSearch = { wynimeNavigator.navigateSubjectSearch() },
                        navigationLayoutType = navigationLayoutType,
                    )
                }
            }
            entry<NavRoutes.SubjectSearch> { route ->
                val navigator = LocalNavigator.current
                val vm = viewModel(key = route.toString()) { SearchViewModel(route.toQuery()) }

                SearchScreen(
                    vm,
                    onNavigateBack = {
                        wynimeNavigator.popBackStack()
                    },
                    onNavigateToSubjectDetails = { subjectId, placeholder ->
                        navigator.navigateSubjectDetails(subjectId, placeholder)
                    },
                    onNavigateToEpisodeDetails = { subjectId, episodeId ->
                        navigator.navigateEpisodeDetails(subjectId, episodeId)
                    },
                    windowInsets = windowInsets,
                )
            }
            entry<NavRoutes.SubjectDetail> { route ->
                val vm = viewModel<SubjectDetailsViewModel>(key = route.subjectId.toString()) {
                    val placeholder = route.placeholder?.run {
                        SubjectInfo.createPlaceholder(id, name, coverUrl, nameCN)
                    }
                    SubjectDetailsViewModel(route.subjectId, placeholder)
                }
                SubjectDetailsScreen(
                    vm,
                    onPlay = { wynimeNavigator.navigateEpisodeDetails(route.subjectId, it) },
                    onLoadErrorRetry = { vm.reload() },
                    onClickTag = {
                        wynimeNavigator.navigateSubjectSearch(NavRoutes.SubjectSearch(tags = listOf(it.name)))
                    },
                    windowInsets = windowInsets,
                    navigationIcon = {
                        Row {
                            BackNavigationIconButton(
                                {
                                    wynimeNavigator.popBackStack(route, inclusive = true)
                                },
                            )
                            TopAppBarActionButton(
                                {
                                    wynimeNavigator.popBackOrNavigateToMain(mainSceneInitialPage)
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.Home,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                )
            }
            entry<NavRoutes.EpisodeDetail> { route ->
                val context = LocalContext.current
                val vm = viewModel<EpisodeViewModel>(
                    key = route.toString(),
                ) {
                    EpisodeViewModel(
                        subjectId = route.subjectId,
                        initialEpisodeId = route.episodeId,
                        initialIsFullscreen = false,
                        context,
                    )
                }
                EpisodeScreen(vm, Modifier.fillMaxSize(), windowInsets)
            }
            entry<NavRoutes.Settings> { route ->
                SettingsScreen(
                    viewModel {
                        SettingsViewModel()
                    },
                    onNavigateToBangumiLogin = { wynimeNavigator.navigateBangumiAuthorize() },
                    onNavigateToOAuth = { if (it == OAuthPlatform.BANGUMI) wynimeNavigator.navigateBangumiAuthorize() },
                    loadOpenSourceLibrariesJsons = ::loadOpenSourceLibrariesJsons,
                    Modifier.fillMaxSize(),
                    route.tab,
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                wynimeNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                )
            }
            entry<NavRoutes.PlaybackHistory> { route ->
                PlaybackHistoryScreen(
                    vm = viewModel { PlaybackHistoryViewModel() },
                    onNavigateBack = { wynimeNavigator.popBackStack(route, inclusive = true) },
                    onOpenHistory = { history ->
                        val subjectId = history.subjectId
                        if (subjectId != null) {
                            wynimeNavigator.navigateEpisodeDetails(subjectId, history.episodeId)
                        }
                    },
                    onOpenSyncStatus = {
                        wynimeNavigator.navigatePlaybackHistorySyncStatus()
                    },
                    modifier = Modifier.fillMaxSize(),
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                wynimeNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    windowInsets = windowInsetsWithoutTitleBar,
                )
            }
            entry<NavRoutes.PlaybackHistorySyncStatus> { route ->
                PlaybackHistorySyncStatusScreen(
                    vm = viewModel { PlaybackHistoryViewModel() },
                    onNavigateBack = { wynimeNavigator.popBackStack(route, inclusive = true) },
                    modifier = Modifier.fillMaxSize(),
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                wynimeNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    windowInsets = windowInsetsWithoutTitleBar,
                )
            }

            entry<NavRoutes.Caches> { route ->
                val selfInfo by remember { SelfInfoStateProducer() }.flow.collectAsState(null)
                DownloadManagementScreen(
                    vm = viewModel { createDownloadManagementViewModel() },
                    selfInfo = selfInfo,
                    onPlay = {
                        wynimeNavigator.navigateEpisodeDetails(it.subjectId, it.episodeId)
                    },
                    onClickLogin = { },
                    onNavigateCacheDetail = { wynimeNavigator.navigateCacheDetails(it) },
                    modifier = Modifier.fillMaxSize(),
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                wynimeNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                )
            }
            entry<NavRoutes.CacheDetail> { route ->
                MediaCacheDetailsScreen(
                    viewModel(key = route.toString()) { MediaCacheDetailsPageViewModel(route.cacheId) },
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                wynimeNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                )
            }
            entry<NavRoutes.SubjectRelationGraph> { route ->
                val vm = viewModel<SubjectRelationGraphViewModel>(key = "subject-relation-graph-${route.subjectId}") {
                    SubjectRelationGraphViewModel(route.subjectId)
                }
                SubjectRelationGraphScreen(
                    vm,
                    onClickSubject = {
                        wynimeNavigator.navigateSubjectDetails(
                            it.subjectId,
                            SubjectDetailPlaceholder(it.subjectId, it.name, it.nameCn, it.image),
                        )
                    },
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton({ wynimeNavigator.popBackStack(route, inclusive = true) })
                    },
                )
            }
            entry<NavRoutes.PersonDetail> { route ->
                val vm = viewModel<PersonDetailsViewModel>(key = "person-${route.personId}") {
                    PersonDetailsViewModel(route.personId)
                }
                PersonDetailsScreen(
                    vm,
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton({ wynimeNavigator.popBackStack(route, inclusive = true) })
                    },
                )
            }
            entry<NavRoutes.CharacterDetail> { route ->
                val vm = viewModel<CharacterDetailsViewModel>(key = "character-${route.characterId}") {
                    CharacterDetailsViewModel(route.characterId)
                }
                CharacterDetailsScreen(
                    vm,
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton({ wynimeNavigator.popBackStack(route, inclusive = true) })
                    },
                )
            }
            entry<NavRoutes.SubjectCaches> { route ->
                SubjectDownloadsScreen(
                    vm = viewModel(key = route.toString()) { createSubjectDownloadsViewModel(route.subjectId) },
                    onPlay = { wynimeNavigator.navigateEpisodeDetails(it.subjectId, it.episodeId) },
                    onNavigateDownloadDetail = { wynimeNavigator.navigateCacheDetails(it) },
                    modifier = Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                wynimeNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                )
            }
            entry<NavRoutes.Schedule> { route ->
                val vm = viewModel { ScheduleViewModel() }
                val presentation by vm.presentationFlow.collectAsStateWithLifecycle()
                ScheduleScreen(
                    presentation,
                    onRetry = { vm.refresh() },
                    onClickItem = {
                        wynimeNavigator.navigateSubjectDetails(
                            it.subjectId,
                            placeholder = SubjectDetailPlaceholder(
                                id = it.subjectId,
                                name = it.subjectOriginalTitle,
                                nameCN = it.subjectTitle,
                                coverUrl = it.imageUrl,
                            ),
                        )
                    },
                    Modifier.fillMaxSize(),
                    windowInsets = windowInsets,
                    navigationIcon = {
                        BackNavigationIconButton(
                            {
                                wynimeNavigator.popBackStack(route, inclusive = true)
                            },
                        )
                    },
                    state = vm.pageState,
                )
            }
        },
    )
}

private fun NavRoutes.SubjectSearch.toQuery(): SubjectSearchQuery {
    return SubjectSearchQuery(
        keywords = keyword ?: "",
        tags = tags,
    )
}
