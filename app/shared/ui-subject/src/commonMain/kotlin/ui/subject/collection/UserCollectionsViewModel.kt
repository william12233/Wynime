package com.wynime.app.ui.subject.collection

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.preference.MyCollectionsSettings
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.episode.EpisodeProgressRepository
import com.wynime.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.session.SessionEvent
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.navigation.WynimeNavigator
import com.wynime.app.tools.MonoTasker
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.foundation.launchInBackground
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.app.ui.subject.collection.progress.SubjectProgressStateFactory
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.datasources.api.topic.toggleCollected
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.logging.info
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
open class UserCollectionsViewModel : AbstractViewModel(), KoinComponent {
    lateinit var navigator: WynimeNavigator

    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val episodeProgressRepository: EpisodeProgressRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val setSubjectCollectionTypeOrDeleteUseCase: SetSubjectCollectionTypeOrDeleteUseCase by inject()

    val lazyGridState = LazyGridState()

    private val subjectProgressStateFactory: SubjectProgressStateFactory = SubjectProgressStateFactory(
        episodeProgressRepository,
    )

    val myCollectionsSettings: MyCollectionsSettings by settingsRepository.uiSettings.flow
        .map { it.myCollections }
        .produceState(MyCollectionsSettings.Default)

    private val fullSyncTasker = MonoTasker(backgroundScope)
    val fullSyncState: MutableStateFlow<BangumiSyncState?> = MutableStateFlow(null)
    val isFullSyncRunning: StateFlow<Boolean> get() = fullSyncTasker.isRunning

    private val countsRestarter = FlowRestarter()

    val state = UserCollectionsState(
        startSearch = { subjectCollectionRepository.subjectCollectionsPager(it) },
        collectionCountsState = subjectCollectionRepository.subjectCollectionCountsFlow()
            .restartable(countsRestarter)
            .produceState(null),
        subjectProgressStateFactory,
        createEditableSubjectCollectionTypeState = { createEditableSubjectCollectionTypeState(it) },
        backgroundScope,
    )

    init {
        launchInBackground {
            sessionStateProvider.eventFlow.filter { it is SessionEvent.NewLogin }.collectLatest {
                logger.info { "登录信息变更, 清空缓存" }

                refreshCollections()
            }
        }

        launchInBackground {

            subjectCollectionRepository.collectionsInvalidated.collect {
                logger.info { "收藏缓存已失效, 刷新列表" }
                refreshCollections()
            }
        }
    }

    private fun refreshCollections() {
        state.refresh()
        countsRestarter.restart()
    }

    fun fullSync() {
        if (fullSyncTasker.isRunning.value) return
        fullSyncTasker.launch {
            fullSyncState.value = BangumiSyncState.Preparing
            supervisorScope {
                var failure: Throwable? = null
                val syncJob = launch {
                    try {
                        subjectCollectionRepository.performBangumiFullSync()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        failure = e
                    }
                }
                while (syncJob.isActive) {
                    runCatching { subjectCollectionRepository.getBangumiFullSyncState() }
                        .getOrNull()
                        ?.let { fullSyncState.value = it }
                    delay(200.milliseconds)
                }
                syncJob.join()
                val terminalState = runCatching { subjectCollectionRepository.getBangumiFullSyncState() }
                    .getOrNull()
                fullSyncState.value = terminalState ?: failure?.let {
                    BangumiSyncState.Finished(
                        savedCount = 0,
                        error = null,
                        localError = it.message ?: it::class.simpleName,
                    )
                }
            }
        }
    }

    private fun createEditableSubjectCollectionTypeState(collection: SubjectCollectionInfo): EditableSubjectCollectionTypeState =

        EditableSubjectCollectionTypeState(
            selfCollectionTypeFlow = flowOf(collection.collectionType),
            hasAnyUnwatched = hasAnyUnwatched@{
                val collections =
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(collection.subjectId)
                        .firstOrNull() ?: return@hasAnyUnwatched true
                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = { setSubjectCollectionTypeOrDeleteUseCase(collection.subjectId, it) },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(collection.subjectId)
            },
            backgroundScope,
        )

    suspend fun toggleEpisodeCollection(
        subjectId: Int,
        episodeId: Int,
        collectionType: UnifiedCollectionType
    ): LoadError? = LoadError.runAndWrapOrThrowCancellation {
        episodeCollectionRepository.setEpisodeCollectionType(
            subjectId,
            episodeId,
            collectionType.toggleCollected(),
        )
    }
}
