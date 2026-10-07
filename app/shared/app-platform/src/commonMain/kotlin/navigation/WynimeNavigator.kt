package com.wynime.app.navigation

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.EpisodeEnter

interface WynimeNavigator {

    fun setBackStack(backStack: SnapshotStateList<NavRoutes>)

    fun isBackStackReady(): Boolean

    suspend fun awaitBackStack(): List<NavRoutes>

    val backStack: List<NavRoutes>

    fun navigate(route: NavRoutes)

    fun popBackStack()

    fun popBackStack(route: NavRoutes, inclusive: Boolean)

    fun navigateSubjectDetails(
        subjectId: Int,
        placeholder: SubjectDetailPlaceholder?,
    ) {
        navigate(NavRoutes.SubjectDetail(subjectId, placeholder))
    }

    fun navigateSubjectRelationGraph(subjectId: Int) {
        navigate(NavRoutes.SubjectRelationGraph(subjectId))
    }

    fun navigateSubjectCaches(subjectId: Int) {
        navigate(NavRoutes.SubjectCaches(subjectId))
    }

    fun navigatePersonDetails(personId: Int) {
        navigate(NavRoutes.PersonDetail(personId))
    }

    fun navigateCharacterDetails(characterId: Int) {
        navigate(NavRoutes.CharacterDetail(characterId))
    }

    fun navigateEpisodeDetails(
        subjectId: Int,
        episodeId: Int,
        fullscreen: Boolean = false,
        force: Boolean = false,
    ) {
        if (!force && !EpisodeNavigationGuardRegistry.checkOrNotifyDenied(subjectId, episodeId)) {
            return
        }

        popBackStack(NavRoutes.EpisodeDetail(subjectId, episodeId), inclusive = true)
        navigate(NavRoutes.EpisodeDetail(subjectId, episodeId))
        Analytics.recordEvent(
            EpisodeEnter,
            mapOf("subject_id" to subjectId, "episode_id" to episodeId),
        )
    }

    fun navigateMain(
        page: MainScreenPage,
        popUpTargetInclusive: NavRoutes? = null,
    )

    fun popBackOrNavigateToMain(mainSceneInitialPage: MainScreenPage)

    fun navigateLogin() {
        navigateBangumiAuthorize()
    }

    fun navigateOAuthAuthorize(provider: String) {
        navigate(NavRoutes.OAuthAuthorize(provider))
    }

    fun navigateBangumiAuthorize() {
        navigateOAuthAuthorize("bangumi")
    }

    fun navigatePlaybackHistorySyncStatus() {
        navigate(NavRoutes.PlaybackHistorySyncStatus)
    }

    fun navigateSettings(tab: SettingsTab? = null) {
        navigate(NavRoutes.Settings(tab))
    }

    fun navigateSubjectSearch(search: NavRoutes.SubjectSearch = NavRoutes.SubjectSearch()) {
        navigate(search)
    }

    fun navigateSubjectSearch(tag: String) {
        navigate(NavRoutes.SubjectSearch(tags = listOf(tag)))
    }

    fun navigateCaches() {
        navigate(NavRoutes.Caches)
    }

    fun navigateCacheDetails(cacheId: String) {
        navigate(NavRoutes.CacheDetail(cacheId))
    }

    fun navigateSchedule() {
        navigate(NavRoutes.Schedule)
    }

    fun navigatePlaybackHistory() {
        navigate(NavRoutes.PlaybackHistory)
    }

}

fun WynimeNavigator(): WynimeNavigator = WynimeNavigatorImpl()

private class WynimeNavigatorImpl : WynimeNavigator {
    private val _backStack: MutableStateFlow<SnapshotStateList<NavRoutes>?> = MutableStateFlow(null)

    private val currentBackStack: SnapshotStateList<NavRoutes>
        get() = _backStack.value ?: error("Back stack is not yet set")

    override val backStack: List<NavRoutes>
        get() = currentBackStack

    override fun setBackStack(backStack: SnapshotStateList<NavRoutes>) {
        _backStack.value = backStack
    }

    override fun isBackStackReady(): Boolean = _backStack.value != null

    override suspend fun awaitBackStack(): List<NavRoutes> = _backStack.filterNotNull().first()

    override fun navigate(route: NavRoutes) {
        currentBackStack.add(route)
    }

    override fun popBackStack() {
        val stack = currentBackStack

        if (stack.size <= 1) return
        stack.removeAt(stack.lastIndex)
    }

    override fun popBackStack(route: NavRoutes, inclusive: Boolean) {
        val stack = currentBackStack
        val index = stack.indexOfLast { it == route }
        if (index == -1) return
        val targetSize = if (inclusive) index else index + 1
        stack.popTo(targetSize)
    }

    override fun navigateMain(page: MainScreenPage, popUpTargetInclusive: NavRoutes?) {
        val stack = currentBackStack

        Snapshot.withMutableSnapshot {
            if (popUpTargetInclusive != null) {
                val index = stack.indexOfLast { it == popUpTargetInclusive }
                if (index != -1) {
                    stack.popTo(index, keepAtLeastOne = false)
                }
            }
            stack.add(NavRoutes.Main(page))
        }
    }

    override fun popBackOrNavigateToMain(mainSceneInitialPage: MainScreenPage) {
        val stack = currentBackStack
        val firstMain = stack.indexOfFirst { it is NavRoutes.Main }
        if (firstMain != -1) {
            stack.popTo(firstMain + 1)
            return
        }
        Snapshot.withMutableSnapshot {
            stack.clear()
            stack.add(NavRoutes.Main(mainSceneInitialPage))
        }
    }

    private fun SnapshotStateList<NavRoutes>.popTo(targetSize: Int, keepAtLeastOne: Boolean = true) {
        val size = if (keepAtLeastOne) targetSize.coerceAtLeast(1) else targetSize
        while (this.size > size) {
            removeAt(lastIndex)
        }
    }
}

inline fun <reified T : NavRoutes> WynimeNavigator.findLast(): T? =
    backStack.lastOrNull { it is T } as T?

inline fun <reified T : NavRoutes> WynimeNavigator.findFirst(): T? =
    backStack.firstOrNull { it is T } as T?

val LocalNavigator = compositionLocalOf<WynimeNavigator> {
    error("Navigator not found")
}

@Composable
inline fun OverrideNavigation(
    noinline newNavigator: @DisallowComposableCalls (WynimeNavigator) -> WynimeNavigator,
    crossinline content: @Composable () -> Unit
) {
    val currentState = rememberUpdatedState(LocalNavigator.current)
    val newNavigatorState = rememberUpdatedState(newNavigator)
    val new by remember {
        derivedStateOf {
            newNavigatorState.value(currentState.value)
        }
    }
    CompositionLocalProvider(LocalNavigator provides new) {
        content()
    }
}
