/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration

import androidx.compose.runtime.Stable
import androidx.paging.cachedIn
import androidx.paging.filter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.network.BangumiCalendarDay
import me.him188.ani.app.data.network.BangumiCalendarRepository
import me.him188.ani.app.data.network.RecommendationRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.data.repository.subject.FollowedSubjectsRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.logging.info
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Stable
open class ExplorationPageViewModel(private val koin: Koin = GlobalKoin) : AbstractViewModel(), KoinComponent {
    override fun getKoin(): Koin = koin

    private val calendarRepository: BangumiCalendarRepository by inject()
    private val recommendationRepository: RecommendationRepository by inject()
    private val followedSubjectsRepository: FollowedSubjectsRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val horizontalScrollTipFlow =
        settingsRepository.oneshotActionConfig.flow.map { it.horizontalScrollTip }
    private val todayUpdatesStateMutable = MutableStateFlow<TodayUpdatesUiState>(TodayUpdatesUiState.InitialLoading)
    private val todayUpdatesRefreshMutex = Mutex()
    val todayUpdatesState: StateFlow<TodayUpdatesUiState> = todayUpdatesStateMutable.asStateFlow()

    init {
        logger.info { "ExplorationPageViewModel created." }
        backgroundScope.launch {
            refreshTodayUpdates()
            while (true) {
                val now = Clock.System.now()
                val timeZone = TimeZone.currentSystemDefault()
                delay(delayUntilNextMidnight(now, timeZone))
                refreshTodayUpdates()
            }
        }
    }

    fun retryTodayUpdates() {
        backgroundScope.launch { refreshTodayUpdates(forceRefresh = true) }
    }

    private suspend fun refreshTodayUpdates(forceRefresh: Boolean = false) {
        todayUpdatesRefreshMutex.withLock {
            try {
                val now = Clock.System.now()
                val timeZone = TimeZone.currentSystemDefault()
                val weekdayId = now.toLocalDateTime(timeZone).date.dayOfWeek.isoDayNumber
                val calendarDays = calendarRepository.getCalendarDays(forceRefresh)
                todayUpdatesStateMutable.value = TodayUpdatesUiState.Content(
                    items = calendarDays.toTodayUpdateSubjectInfos(weekdayId),
                )
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                todayUpdatesStateMutable.value = TodayUpdatesUiState.Error(LoadError.fromException(e))
            }
        }
    }

    val explorationPageState: ExplorationPageState = ExplorationPageState(
        todayUpdatesState = todayUpdatesState,
        onRetryTodayUpdates = ::retryTodayUpdates,
        followedSubjectsPager = combine(
            settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode },
            followedSubjectsRepository.followedSubjectsPager(),
        ) { nsfwMode, subjects ->
            if (nsfwMode != NsfwMode.HIDE) return@combine subjects
            subjects.filter { !it.subjectInfo.nsfw }
        }.cachedIn(backgroundScope),
        recommendationPager = recommendationRepository.recommendedSubjectsPager().cachedIn(backgroundScope),
        horizontalScrollTipFlow = horizontalScrollTipFlow,
        onSetDisableHorizontalScrollTip = {
            backgroundScope.launch {
                settingsRepository.oneshotActionConfig.update { copy(horizontalScrollTip = false) }
            }
        },
//            .onStart<List<FollowedSubjectInfo?>> {
//                emit(arrayOfNulls<FollowedSubjectInfo>(10).toList())
//            }
    )

}

internal fun delayUntilNextMidnight(now: Instant, timeZone: TimeZone): Duration {
    val today = now.toLocalDateTime(timeZone).date
    return (today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone) - now)
        .coerceAtLeast(1.milliseconds)
}

sealed interface TodayUpdatesUiState {
    data object InitialLoading : TodayUpdatesUiState

    data class Content(val items: List<TodayUpdateSubjectInfo>) : TodayUpdatesUiState

    data class Error(val error: LoadError) : TodayUpdatesUiState
}

data class TodayUpdateSubjectInfo(
    val bangumiId: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
) {
    val displayName: String
        get() = nameCn.ifBlank { name }.ifBlank { "Bangumi #$bangumiId" }
}

internal fun List<BangumiCalendarDay>.toTodayUpdateSubjectInfos(weekdayId: Int): List<TodayUpdateSubjectInfo> {
    return asSequence()
        .filter { it.weekdayId == weekdayId }
        .flatMap { it.items.asSequence() }
        .filter { it.id > 0 && it.type == 2 }
        .distinctBy { it.id }
        .map { item ->
            TodayUpdateSubjectInfo(
                bangumiId = item.id,
                name = item.name,
                nameCn = item.nameCn,
                imageLarge = item.imageLarge,
            )
        }
        .toList()
}
