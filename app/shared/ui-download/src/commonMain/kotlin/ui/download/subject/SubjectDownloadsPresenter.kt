package com.wynime.app.ui.download.subject

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.nameCnOrName
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.download.DownloadOperation
import com.wynime.app.domain.media.download.DownloadOperations
import com.wynime.app.domain.media.download.DownloadRequestSession
import com.wynime.app.domain.media.download.DownloadRequestSessionFactory
import com.wynime.app.domain.media.download.DownloadRequestState
import com.wynime.app.domain.media.download.DownloadSnapshot
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.ui.download.DownloadOperationRunner
import com.wynime.app.ui.download.components.toDownloadItem
import com.wynime.app.ui.mediafetch.MediaSourceInfoProvider
import com.wynime.datasources.api.Media
import com.wynime.utils.coroutines.childScope

class SubjectDownloadsPresenter(
    val subjectId: Int,
    parentScope: CoroutineScope,
    subjects: SubjectCollectionRepository,
    histories: EpisodePlayHistoryRepository,
    settings: SettingsRepository,
    sources: MediaSourceManager,
    downloadManager: MediaDownloadManager,
    private val sessionFactory: DownloadRequestSessionFactory,
    operations: DownloadOperations,
    initialTitle: String? = null,
) : AutoCloseable {
    private val scope = parentScope.childScope()
    private val reloadCount = MutableStateFlow(0)
    private val operationRunner = DownloadOperationRunner(operations, scope)
    private val session = MutableStateFlow<DownloadRequestSession?>(null)
    private val requestState: Flow<DownloadRequestState?> = session.flatMapLatest { it?.state ?: flowOf(null) }

    val selectorSettings = settings.mediaSelectorSettings.flow
    val sourceInfoProvider = MediaSourceInfoProvider(sources::infoFlowByMediaSourceId)

    private val subjectLoad = MutableStateFlow(LoadState<SubjectCollectionInfo>())
    private val downloadsLoad = MutableStateFlow(LoadState<List<DownloadSnapshot>>())

    private val subject = reloadCount.flatMapLatest { subjects.subjectCollectionFlow(subjectId).asLoadState(subjectLoad) }
    private val downloads = reloadCount.flatMapLatest { downloadManager.snapshots(subjectId).asLoadState(downloadsLoad) }

    val uiState: StateFlow<SubjectDownloadsUiState> =
        combine(subject, downloads, histories.flow, requestState) { subject, downloads, histories, request ->
            val info = subject.value
            val historyByEpisode = histories.associateBy { it.episodeId }
            val items = downloads.value.orEmpty().map { snapshot ->
                val history = snapshot.metadata.episodeId.toIntOrNull()?.let { historyByEpisode[it] }
                snapshot.toDownloadItem(info?.collectionType, history)
            }
            SubjectDownloadsUiState(
                title = info?.subjectInfo?.nameCnOrName ?: initialTitle,
                items = buildSubjectDownloadItems(info?.downloadEpisodes().orEmpty(), items),
                downloads = items,
                totalEpisodes = info?.episodes?.size,
                episodesLoading = subject.loading,
                downloadsLoading = downloads.loading,
                episodesFailed = subject.failed,
                downloadsFailed = downloads.failed,
                request = request.toRequestUiState(),
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), SubjectDownloadsUiState(title = initialTitle))

    private val currentPicker = MutableStateFlow<DownloadMediaPickerState?>(null)

    val requestDialogs: StateFlow<DownloadRequestDialogState?> = requestState
        .map { it.toDialogState() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    fun reload() {
        subjectLoad.update { it.copy(loading = true, failed = false) }
        downloadsLoad.update { it.copy(loading = true, failed = false) }
        reloadCount.update { it + 1 }
    }

    val operationFailures: StateFlow<Int> get() = operationRunner.failedCount

    fun dismissOperationFailures() = operationRunner.dismissFailures()

    fun requestDownload(episodeId: Int): Boolean {
        val current = session.value
        when (val state = current?.state?.value) {
            is DownloadRequestState.AwaitingSelection,
            is DownloadRequestState.SelectingEpisodes -> {
                if (state.episodeId == episodeId) return false
                current?.cancel()
            }

            is DownloadRequestState.Working -> return false
            null, is DownloadRequestState.Finished -> Unit
        }
        val created = sessionFactory.create(subjectId, listOf(episodeId), scope)
        session.value = created
        created.start()
        return true
    }

    fun cancelRequest() {
        session.getAndUpdate { null }?.cancel()
    }

    fun selectMedia(episodeId: Int, media: Media) {
        session.value?.select(episodeId, media)
    }

    fun confirmEpisodes(episodeIds: Set<Int>) {
        session.value?.confirmEpisodes(episodeIds)
    }

    fun backToMediaSelection() {
        session.value?.backToSelection()
    }

    fun pauseDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Pause)
    fun resumeDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Resume)
    fun deleteDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Delete)
    fun pauseAll() = pauseDownloads(uiState.value.downloads.mapTo(hashSetOf()) { it.id })
    fun resumeAll() = resumeDownloads(uiState.value.downloads.mapTo(hashSetOf()) { it.id })

    val isClosed: Boolean get() = !scope.isActive

    override fun close() {
        scope.cancel()
    }

    private fun DownloadRequestState?.toDialogState(): DownloadRequestDialogState? {
        if (this !is DownloadRequestState.AwaitingSelection && this !is DownloadRequestState.SelectingEpisodes) {
            currentPicker.value = null
        }
        return when (this) {
            null -> null
            is DownloadRequestState.AwaitingSelection -> DownloadRequestDialogState(
                selection = pickerFor(episodeId, fetchSession, selector),
            )

            is DownloadRequestState.SelectingEpisodes -> DownloadRequestDialogState(
                selection = pickerFor(episodeId, fetchSession, selector),
                episodePicker = DownloadEpisodePickerState(episodeId, chosen, options),
            )

            is DownloadRequestState.Finished -> if (error == null) null else DownloadRequestDialogState(failed = true)
            is DownloadRequestState.Preparing, is DownloadRequestState.Creating -> DownloadRequestDialogState()
        }
    }

    private fun pickerFor(episodeId: Int, fetchSession: MediaFetchSession, selector: MediaSelector): DownloadMediaPickerState {
        currentPicker.value?.let { picker -> if (picker.fetchSession === fetchSession) return picker }
        return DownloadMediaPickerState(episodeId, fetchSession, selector)
            .also { currentPicker.value = it }
    }
}

private fun DownloadRequestState?.toRequestUiState() = DownloadRequestUiState(
    episodeIds = this?.pendingEpisodeIds?.toSet().orEmpty(),
    busy = this is DownloadRequestState.Preparing || this is DownloadRequestState.Creating,
    canCancel = this != null && this !is DownloadRequestState.Finished,
)

private data class LoadState<T>(val value: T? = null, val loading: Boolean = true, val failed: Boolean = false)

private fun <T> Flow<T>.asLoadState(holder: MutableStateFlow<LoadState<T>>): Flow<LoadState<T>> = flow {
    emit(holder.value)
    try {
        collect { value -> emit(holder.updateAndGet { LoadState(value, loading = false) }) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emit(holder.updateAndGet { it.copy(loading = false, failed = true) })
    }
}

class SubjectDownloadsPresenterFactory(
    private val subjects: SubjectCollectionRepository,
    private val histories: EpisodePlayHistoryRepository,
    private val settings: SettingsRepository,
    private val sources: MediaSourceManager,
    private val downloadManager: MediaDownloadManager,
    private val sessionFactory: DownloadRequestSessionFactory,
    private val operations: DownloadOperations,
) {
    fun create(subjectId: Int, parentScope: CoroutineScope, initialTitle: String? = null): SubjectDownloadsPresenter =
        SubjectDownloadsPresenter(
            subjectId, parentScope, subjects, histories, settings, sources, downloadManager, sessionFactory, operations,
            initialTitle,
        )
}
