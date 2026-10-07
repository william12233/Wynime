package com.wynime.app.ui.download

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.data.repository.subject.OfflineSubjectDisplayInfo
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.domain.media.download.DownloadOperation
import com.wynime.app.domain.media.download.DownloadOperations
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.ui.download.components.DownloadItem
import com.wynime.app.ui.download.components.SubjectDownloadGroup
import com.wynime.app.ui.download.components.toDownloadItem
import com.wynime.app.ui.download.subject.SubjectDownloadsPresenter
import com.wynime.app.ui.download.subject.SubjectDownloadsPresenterFactory
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.coroutines.sampleWithInitial

class DownloadManagementViewModel(
    downloadManager: MediaDownloadManager,
    subjects: SubjectCollectionRepository,
    histories: EpisodePlayHistoryRepository,
    operations: DownloadOperations,
    private val presenters: SubjectDownloadsPresenterFactory,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(coroutineContext) {
    private val operationRunner = DownloadOperationRunner(operations, backgroundScope)

    private val currentSubjectPresenter = MutableStateFlow<SubjectDownloadsPresenter?>(null)

    val subjectPresenter: StateFlow<SubjectDownloadsPresenter?> = currentSubjectPresenter.asStateFlow()

    fun selectSubject(subjectId: Int?, subjectName: String? = null) {
        val previous = currentSubjectPresenter.value
        if (previous?.subjectId == subjectId) return
        currentSubjectPresenter.value = subjectId?.let { presenters.create(it, backgroundScope, subjectName) }
        previous?.close()
    }
    private val downloads = downloadManager.snapshots().shareInBackground()

    private val subjectMetadata = downloads
        .map { list -> list.mapTo(hashSetOf()) { it.metadata.subjectId.toIntOrNull() ?: 0 } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    ids.map { id ->
                        combine(
                            subjects.getSubjectCollectionTypeOffline(id).onStart { emit(null) },
                            subjects.getSubjectDisplayInfoOffline(id).onStart { emit(null) },
                        ) { type, info -> id to SubjectMetadata(type, info) }
                    },
                ) { it.toMap() }
            }
        }
    private val overallStats = downloadManager.overallStats.sampleWithInitial(1.seconds)

    val uiState = combine(downloads, subjectMetadata, histories.flow, overallStats) { downloads, metadata, histories, stats ->
        val historyByEpisode = histories.associateBy { it.episodeId }
        val groups = downloads.groupBy { it.metadata.subjectId.toIntOrNull() ?: 0 }.map { (subjectId, snapshots) ->
            val subject = metadata[subjectId]
            val entries = snapshots.map { snapshot ->
                val history = snapshot.metadata.episodeId.toIntOrNull()?.let { historyByEpisode[it] }
                snapshot.toDownloadItem(subject?.type, history)
            }
            SubjectDownloadGroup(
                subjectId = subjectId,
                subjectName = subject?.info?.displayName ?: entries.first().subjectName,
                entries = entries,
                collectionType = subject?.type,
                imageUrl = subject?.info?.imageThumb,
                totalEpisodeCount = subject?.info?.totalEpisodes?.takeIf { it > 0 },
            )
        }.sortedWith(

            compareByDescending<SubjectDownloadGroup> { it.hasUnfinished }
                .thenByDescending { it.entries.maxOfOrNull { entry -> entry.creationTime ?: 0 } },
        )
        DownloadManagementUiState(stats, groups, isLoading = false)
    }.stateInBackground(DownloadManagementUiState.Placeholder)

    fun pauseDownload(item: DownloadItem) = operationRunner.run(setOf(item.id), DownloadOperation.Pause)
    fun resumeDownload(item: DownloadItem) = operationRunner.run(setOf(item.id), DownloadOperation.Resume)
    fun deleteDownload(item: DownloadItem) = operationRunner.run(setOf(item.id), DownloadOperation.Delete)

    val operationFailures: StateFlow<Int> get() = operationRunner.failedCount

    fun dismissOperationFailures() = operationRunner.dismissFailures()

    private data class SubjectMetadata(val type: UnifiedCollectionType?, val info: OfflineSubjectDisplayInfo?)
}
