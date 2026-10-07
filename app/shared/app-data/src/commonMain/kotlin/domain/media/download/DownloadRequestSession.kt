package com.wynime.app.domain.media.download

import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.fetch.SubjectMediaFetchSessionRegistry
import com.wynime.app.domain.media.fetch.create
import com.wynime.app.domain.media.fetch.createFetchFetchSession
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorFactory
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.isLocalCache
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn

sealed interface DownloadRequestState {

    val pendingEpisodeIds: List<Int>

    sealed interface Working : DownloadRequestState {

        val episodeId: Int
    }

    data class Preparing(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
    ) : Working

    class AwaitingSelection internal constructor(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
        val fetchSession: MediaFetchSession,
        val selector: MediaSelector,
        internal val choice: CompletableDeferred<Media>,
    ) : Working

    class SelectingEpisodes internal constructor(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
        val fetchSession: MediaFetchSession,
        val selector: MediaSelector,
        val chosen: Media,

        val options: List<DownloadEpisodeOption>,
        internal val decision: CompletableDeferred<Set<Int>?>,
    ) : Working

    data class Creating(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
    ) : Working

    data class Finished(val error: Throwable? = null) : DownloadRequestState {
        override val pendingEpisodeIds: List<Int> get() = emptyList()
    }
}

data class DownloadEpisodeOption(
    val episodeId: Int,
    val sort: EpisodeSort,
    val name: String,
    val availability: Availability,

    val resourceTitle: String?,

    val isCurrent: Boolean,
) {
    enum class Availability {
        AVAILABLE,
        ALREADY_DOWNLOADED,
        UNMATCHED,
    }
}

class DownloadRequestSession internal constructor(
    val subjectId: Int,
    episodeIds: List<Int>,
    private val subjects: SubjectCollectionRepository,
    private val preferences: EpisodePreferencesRepository,
    private val sources: MediaSourceManager,
    private val selectors: MediaSelectorFactory,
    private val downloadManager: MediaDownloadManager,
    private val addDownload: AddDownloadUseCase,
    parentScope: CoroutineScope,
    private val sharedFetchSessionRegistry: SubjectMediaFetchSessionRegistry? = null,
) {
    val episodeIds: List<Int> = episodeIds.distinct().also {
        require(it.isNotEmpty()) { "episodeIds must not be empty" }
    }

    private val scope = parentScope.childScope()
    private val started = atomic(false)
    private val mutableState = MutableStateFlow<DownloadRequestState>(
        DownloadRequestState.Preparing(this.episodeIds.first(), this.episodeIds),
    )
    val state: StateFlow<DownloadRequestState> = mutableState.asStateFlow()

    private val created = mutableListOf<ExistingDownload>()

    init {

        scope.coroutineContext.job.invokeOnCompletion { finish(error = null) }
    }

    fun start() {
        check(started.compareAndSet(expect = false, update = true)) { "Session has already been started" }
        scope.launch { run() }
    }

    fun select(episodeId: Int, media: Media): Boolean {
        val current = state.value as? DownloadRequestState.AwaitingSelection ?: return false
        if (current.episodeId != episodeId) return false
        return current.choice.complete(media)
    }

    fun confirmEpisodes(episodeIds: Set<Int>): Boolean {
        val current = state.value as? DownloadRequestState.SelectingEpisodes ?: return false
        return current.decision.complete(episodeIds)
    }

    fun backToSelection(): Boolean {
        val current = state.value as? DownloadRequestState.SelectingEpisodes ?: return false
        return current.decision.complete(null)
    }

    fun cancel() {
        scope.cancel()
        finish(error = null)
    }

    private fun finish(error: Throwable?) {
        mutableState.update { if (it is DownloadRequestState.Finished) it else DownloadRequestState.Finished(error) }
    }

    private suspend fun run() {
        try {
            val remaining = ArrayDeque(episodeIds)
            while (remaining.isNotEmpty()) {
                val handled = processEpisode(remaining.first(), remaining.toList())
                remaining.removeAll { it in handled }
            }
            finish(error = null)
        } catch (e: CancellationException) {
            finish(error = null)
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Download request for subject $subjectId stopped at episode ${state.value.pendingEpisodeIds.firstOrNull()}" }
            finish(e)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun processEpisode(episodeId: Int, pending: List<Int>): Set<Int> {
        mutableState.value = DownloadRequestState.Preparing(episodeId, pending)
        val collection = subjects.subjectCollectionFlow(subjectId).first()
        val subject = collection.subjectInfo
        val episodes = collection.episodes.map { it.episodeInfo }
        val episode = episodes.firstOrNull { it.episodeId == episodeId }
            ?: throw NoSuchElementException("Episode $episodeId is not in subject $subjectId")
        val existing = existingDownloads()

        if (episode.isAired()) {
            BatchDownloadPlanner.findReusableSeasonMedia(episode, existing.map { it.origin })?.let { media ->
                createAll(subject, listOf(episode to media), pending)
                return setOf(episodeId)
            }
        }

        val batch = awaitSelection(episodeId, pending, subject, episode, episodes, existing)

        return createAll(subject, batch, pending) + episodeId
    }

    private fun setStateUnlessFinished(state: DownloadRequestState) {
        mutableState.update { if (it is DownloadRequestState.Finished) it else state }
    }

    private suspend fun createAll(
        subject: SubjectInfo,
        batch: List<Pair<EpisodeInfo, Media>>,
        pending: List<Int>,
    ): Set<Int> {
        val batchIds = batch.map { (target, _) -> target.episodeId }
        return downloadManager.backgroundScope.async {
            runCatching {
                val handled = mutableSetOf<Int>()
                for ((target, media) in batch) {
                    val pendingNow = batchIds.filter { it !in handled } + pending.filter { it !in batchIds }
                    setStateUnlessFinished(DownloadRequestState.Creating(target.episodeId, pendingNow))
                    addDownload(subject, target, media, MediaCacheMetadata(MediaFetchRequest.create(subject, target)))
                    created += ExistingDownload(media, target.episodeId)
                    handled += target.episodeId
                }
                handled
            }
        }.await().getOrThrow()
    }

    private suspend fun existingDownloads(): List<ExistingDownload> =
        downloadManager.downloadsForSubject(subjectId).first().mapNotNull { download ->
            download.metadata.episodeId.toIntOrNull()?.let { ExistingDownload(download.origin, it) }
        } + created

    private suspend fun awaitSelection(
        episodeId: Int,
        pending: List<Int>,
        subject: SubjectInfo,
        episode: EpisodeInfo,
        episodes: List<EpisodeInfo>,
        existing: List<ExistingDownload>,
    ): List<Pair<EpisodeInfo, Media>> = coroutineScope {
        val request = MediaFetchRequest.create(subject, episode, episodes)
        val fetchSession = sharedFetchSessionRegistry?.get(request)
            ?: sources.createFetchFetchSession(flowOf(request))
        val selector = selectors.create(subjectId, episodeId, fetchSession.cumulativeResults, fetchRequest = fetchSession.latestRequest)

        launch { fetchSession.cumulativeResults.collect() }

        val latestPreference = MutableStateFlow<MediaPreference?>(null)
        launch(start = CoroutineStart.UNDISPATCHED) {
            selector.events.onChangePreference.collect { latestPreference.value = it }
        }

        try {
            while (true) {
                val choice = CompletableDeferred<Media>()
                mutableState.value =
                    DownloadRequestState.AwaitingSelection(episodeId, pending, fetchSession, selector, choice)
                val chosen = choice.await()

                val chosenNames = chosen.lineSubjectNames()
                val group = selector.subjectCandidates.first()
                    .mapNotNull { it.result }
                    .filter { !it.isLocalCache() && it.isSameLineAs(chosen, chosenNames, subject.allNames) }

                val plannable = episodes.filter { it.episodeId == episodeId || it.isAired() }
                val preview = BatchDownloadPlanner.plan(plannable, group, existing, pinned = chosen, pinnedEpisodeId = episodeId)
                val options = episodes.map {
                    it.toOption(preview[it.episodeId] ?: EpisodeDownloadPlan.Uncovered, isCurrent = it.episodeId == episodeId)
                }

                if (options.none { !it.isCurrent && it.availability == DownloadEpisodeOption.Availability.AVAILABLE }) {
                    mutableState.value = DownloadRequestState.Creating(episodeId, pending)
                    selectAndSavePreference(selector, chosen, latestPreference)
                    return@coroutineScope listOfNotNull(preview.getValue(episodeId).mediaOrNull?.let { episode to it })
                }

                val decision = CompletableDeferred<Set<Int>?>()
                mutableState.value = DownloadRequestState.SelectingEpisodes(
                    episodeId, pending, fetchSession, selector, chosen, options, decision,
                )
                val picked = decision.await() ?: continue

                val targets = listOf(episode) + plannable.filter { it.episodeId != episodeId && it.episodeId in picked }
                val plan = BatchDownloadPlanner.plan(targets, group, existing, pinned = chosen, pinnedEpisodeId = episodeId)
                val batch = targets.mapNotNull { target ->
                    plan.getValue(target.episodeId).mediaOrNull?.let { target to it }
                }
                val batchIds = batch.map { (target, _) -> target.episodeId }
                mutableState.value = DownloadRequestState.Creating(episodeId, batchIds + pending.filter { it !in batchIds })
                selectAndSavePreference(selector, chosen, latestPreference)
                return@coroutineScope batch
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    private fun EpisodeInfo.toOption(plan: EpisodeDownloadPlan, isCurrent: Boolean): DownloadEpisodeOption {
        val media = plan.mediaOrNull
        return DownloadEpisodeOption(
            episodeId = episodeId,
            sort = sort,
            name = displayName,
            availability = when (plan) {
                EpisodeDownloadPlan.AlreadyDownloaded -> DownloadEpisodeOption.Availability.ALREADY_DOWNLOADED
                EpisodeDownloadPlan.Uncovered -> DownloadEpisodeOption.Availability.UNMATCHED
                is EpisodeDownloadPlan.Create, is EpisodeDownloadPlan.Reuse -> DownloadEpisodeOption.Availability.AVAILABLE
            },
            resourceTitle = media?.let {
                if (it.kind == MediaSourceKind.WEB) it.properties.episodeName ?: it.originalTitle else it.originalTitle
            },
            isCurrent = isCurrent,
        )
    }

    private suspend fun selectAndSavePreference(
        selector: MediaSelector,
        media: Media,
        latest: StateFlow<MediaPreference?>,
    ) = coroutineScope {
        val broadcast = async(start = CoroutineStart.UNDISPATCHED) { selector.events.onChangePreference.first() }
        val preference = if (selector.select(media)) {
            withTimeoutOrNull(PREFERENCE_BROADCAST_TIMEOUT) { broadcast.await() } ?: latest.value
        } else {
            latest.value
        }
        broadcast.cancel()
        preference?.let { preferences.setMediaPreference(subjectId, it) }
    }

    private companion object {
        private val logger = logger<DownloadRequestSession>()

        private fun EpisodeInfo.isAired(): Boolean = !airDate.isValid || airDate <= PackedDate.now()
        private val PREFERENCE_BROADCAST_TIMEOUT = 5.seconds
    }
}

class DownloadRequestSessionFactory(
    private val subjects: SubjectCollectionRepository,
    private val preferences: EpisodePreferencesRepository,
    private val sources: MediaSourceManager,
    private val selectors: MediaSelectorFactory,
    private val downloadManager: MediaDownloadManager,
    private val addDownload: AddDownloadUseCase,
    private val sharedFetchSessionRegistry: SubjectMediaFetchSessionRegistry? = null,
) {

    fun create(subjectId: Int, episodeIds: List<Int>, parentScope: CoroutineScope): DownloadRequestSession =
        DownloadRequestSession(
            subjectId, episodeIds,
            subjects, preferences, sources, selectors, downloadManager, addDownload,
            parentScope,
            sharedFetchSessionRegistry,
        )
}
