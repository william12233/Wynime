package com.wynime.app.ui.subject.episode

import androidx.annotation.UiThread
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.comment.CommentReportTargetType
import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.models.episode.nameOrNameCn
import com.wynime.app.data.models.episode.renderEpisodeEp
import com.wynime.app.data.models.preference.VideoEnhancementDefaultMode
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.models.preference.parseMpvOptions
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.models.subject.nameCnOrName
import com.wynime.app.data.models.subject.nameOrNameCn
import com.wynime.app.data.models.player.playProgressByEpisodeId
import com.wynime.app.data.network.WynimeCommentReportService
import com.wynime.app.data.repository.RepositoryServiceUnavailableException
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.episode.EpisodeCommentRepository
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepository
import com.wynime.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.comment.PostCommentUseCase
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodeSession
import com.wynime.app.domain.episode.GetSubjectRecommendationUseCase
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.episode.SubjectEpisodeInfoBundle
import com.wynime.app.domain.episode.UnsafeEpisodeSessionApi
import com.wynime.app.domain.episode.episodeIdFlow
import com.wynime.app.domain.episode.getCurrentEpisodeId
import com.wynime.app.domain.episode.infoBundleFlow
import com.wynime.app.domain.episode.infoLoadErrorFlow
import com.wynime.app.domain.episode.mediaSelectorFlow
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.media.DroppedFileMedia
import com.wynime.app.domain.media.cache.EpisodeCacheStatus
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.fetch.MediaSourceResultsFilterer
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.mediasource.instance.GetMediaSourceInstancesUseCase
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.domain.player.CacheProgressProvider
import com.wynime.app.domain.player.extension.AnalyticsExtension
import com.wynime.app.domain.player.extension.AutoSelectExtension
import com.wynime.app.domain.player.extension.MarkAsWatchedExtension
import com.wynime.app.domain.player.extension.ObserveWebMediaSourcePreferenceExtension
import com.wynime.app.domain.player.extension.PlaybackSpeedExtension
import com.wynime.app.domain.player.extension.RememberPlayProgressExtension
import com.wynime.app.domain.player.extension.SaveMediaPreferenceExtension
import com.wynime.app.domain.player.extension.SwitchMediaOnPlayerErrorExtension
import com.wynime.app.domain.player.extension.SwitchNextEpisodeExtension
import com.wynime.app.domain.settings.GetMediaSelectorSettingsUseCase
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.app.platform.Context
import com.wynime.app.ui.comment.BangumiCommentSticker
import com.wynime.app.ui.comment.CommentEditorState
import com.wynime.app.ui.comment.CommentMapperContext
import com.wynime.app.ui.comment.CommentMapperContext.parseToUIComment
import com.wynime.app.ui.comment.CommentMapperContext.toCommentVoteValue
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.comment.EditCommentSticker
import com.wynime.app.ui.comment.UICommentSource
import com.wynime.app.ui.comment.reportSnapshotText
import com.wynime.app.ui.comment.toDataReason
import com.wynime.app.ui.episode.PlayingEpisodeSummary
import com.wynime.app.ui.episode.share.MediaShareData
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.foundation.HasBackgroundScope
import com.wynime.app.ui.foundation.launchInBackground
import com.wynime.app.ui.foundation.lists.PaginatedGroup
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.mediafetch.MediaSelectorState
import com.wynime.app.ui.mediafetch.MediaSourceInfoProvider
import com.wynime.app.ui.mediafetch.createTestMediaSelectorState
import com.wynime.app.ui.mediaselect.summary.MediaSelectorSummary
import com.wynime.app.ui.mediaselect.summary.MediaSelectorSummaryStateProducer
import com.wynime.app.ui.mediaselect.summary.selectedMaybeExcludedMediaFlow
import com.wynime.app.ui.subject.AiringLabelState
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.app.ui.subject.details.state.SubjectDetailsStateFactory
import com.wynime.app.ui.subject.details.state.SubjectDetailsStateLoader
import com.wynime.app.ui.subject.episode.details.EpisodeCarouselState
import com.wynime.app.ui.subject.episode.details.EpisodeDetailsState
import com.wynime.app.ui.subject.episode.statistics.VideoStatistics
import com.wynime.app.ui.subject.episode.statistics.VideoStatisticsCollector
import com.wynime.app.ui.subject.episode.video.PlayerSkipOpEdState
import com.wynime.app.ui.subject.episode.video.sidesheet.EpisodeSelectorState
import com.wynime.app.ui.user.SelfInfoStateProducer
import com.wynime.app.ui.user.SelfInfoUiState
import com.wynime.app.videoplayer.player.applyMpvOptions
import com.wynime.app.videoplayer.player.isMpv
import com.wynime.app.videoplayer.ui.ControllerVisibility
import com.wynime.app.videoplayer.ui.PlayerControllerState
import com.wynime.app.videoplayer.videoenhancement.VideoEnhancementMode
import com.wynime.app.videoplayer.videoenhancement.createVideoEnhancementController
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.topic.isDoneOrDropped
import com.wynime.utils.coroutines.SingleTaskExecutor
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.flowOfEmptyList
import com.wynime.utils.coroutines.flows.flowOfNull
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.coroutines.flows.shareTransparentlyIn
import com.wynime.utils.coroutines.sampleWithInitial
import com.wynime.utils.io.SystemPath
import com.wynime.utils.logging.info
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.chapters
import org.openani.mediamp.metadata.Chapter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import com.wynime.app.data.models.episode.EpisodeInfo

private const val OP_ED_AUTO_SKIP_BASE_SAMPLE_INTERVAL_MILLIS = 1_000L

private fun opEdAutoSkipSampleIntervalMillis(playbackSpeed: Float): Long {
    val effectiveSpeed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (OP_ED_AUTO_SKIP_BASE_SAMPLE_INTERVAL_MILLIS / effectiveSpeed).toLong().coerceAtLeast(1L)
}

@Stable
data class EpisodePageState(
    val selfInfo: SelfInfoUiState,
    val mediaSelectorState: MediaSelectorState,
    val subjectPresentation: SubjectPresentation,
    val episodePresentation: EpisodePresentation,
    val isLoading: Boolean = false,
    val loadError: EpisodePageLoadError? = null,
    val isPlaceholder: Boolean = false,
    val playingEpisodeSummary: PlayingEpisodeSummary?,
    val mediaSelectorSummary: MediaSelectorSummary,
    val fetchRequest: MediaFetchRequest?,
    val shareData: MediaShareData,
)

sealed class EpisodePageLoadError {

    data class SubjectError(
        val loadError: LoadError,
    ) : EpisodePageLoadError()

    data class SeriesError(
        val loadError: LoadError,
    ) : EpisodePageLoadError()
}

@Stable
open class EpisodeViewModel(
    val subjectId: Int,
    initialEpisodeId: Int,
    initialIsFullscreen: Boolean = false,
    context: Context,
    val getCurrentDate: () -> PackedDate = { PackedDate.now() },
    private val koin: Koin = GlobalKoin,
) : KoinComponent, AbstractViewModel(), HasBackgroundScope {

    private val playerStateFactory: MediampPlayerFactory<*> by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val downloadManager: MediaDownloadManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val episodePlayHistoryRepository: EpisodePlayHistoryRepository by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodeCommentRepository: EpisodeCommentRepository by inject()
    private val commentReportService: WynimeCommentReportService by inject()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()
    private val postCommentUseCase: PostCommentUseCase by inject()
    private val getMediaSelectorSettings: GetMediaSelectorSettingsUseCase by inject()
    private val getMediaSourceInstances: GetMediaSourceInstancesUseCase by inject()
    val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase by inject()
    private val getSubjectRecommendations: GetSubjectRecommendationUseCase by inject()
    private val setSubjectCollectionTypeOrDeleteUseCase: SetSubjectCollectionTypeOrDeleteUseCase by inject()
    private val getPreferredWebMediaSource: GetPreferredWebMediaSourceUseCase by inject()
    private val webSessionManager: WebSessionManager by inject()

    protected open val isAutoSkipOpEdAllowed: Boolean get() = true

    private val tasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    val player: MediampPlayer = playerStateFactory
        .create(context, backgroundScope.coroutineContext)
        .apply {
            if (!isMpv()) return@apply

            runBlocking { applyCustomOptions() }
        }

    val videoEnhancement = createVideoEnhancementController(
        player,
        settingsRepository.playerKernelConfig.flow,
        backgroundScope.coroutineContext,
    )

    private val playbackSpeedOverride = MutableStateFlow<Float?>(null)

    private val playbackSpeedFlow: Flow<Float> = combine(
        settingsRepository.videoScaffoldConfig.flow,
        playbackSpeedOverride,
    ) { config, override ->
        override ?: config.playbackSpeed
    }.distinctUntilChanged()

    @OptIn(UnsafeEpisodeSessionApi::class)
    protected val fetchPlayState = EpisodeFetchSelectPlayState(
        subjectId, initialEpisodeId, player, backgroundScope,
        extensions = listOf(
            AnalyticsExtension,
            PlaybackSpeedExtension.Factory(playbackSpeedFlow),
            RememberPlayProgressExtension,
            MarkAsWatchedExtension,
            SwitchNextEpisodeExtension.Factory(
                getNextEpisode = { currentEpisodeId ->
                    val list = episodeCollectionsFlow.first()
                    val subject = subjectCollectionFlow.first()
                    val currentIndex = list.indexOfFirst { it.episodeId == currentEpisodeId }
                    if (currentIndex == -1) {
                        null
                    } else {
                        val nextEpisode = list.getOrNull(currentIndex + 1) ?: return@Factory null

                        if (!nextEpisode.episodeInfo.isKnownCompleted(subject.recurrence)) {
                            null
                        } else {
                            nextEpisode.episodeId
                        }
                    }
                },
            ),
            SwitchMediaOnPlayerErrorExtension,
            AutoSelectExtension,
            SaveMediaPreferenceExtension,
            ObserveWebMediaSourcePreferenceExtension,
        ),
        koin,
        sharingStarted = SharingStarted.WhileSubscribed(5_000),
        analyticsContext = object : EpisodeFetchSelectPlayState.AnalyticsContext {
            override suspend fun isFullscreen(): Boolean? {
                return withContext(Dispatchers.Main) { this@EpisodeViewModel.isFullscreen }
            }
        },
    )

    val mediaResolver: MediaResolver get() = fetchPlayState.playerSession.mediaResolver

    @UnsafeEpisodeSessionApi
    private val episodeIdFlow get() = fetchPlayState.episodeIdFlow

    @UnsafeEpisodeSessionApi
    private val subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?> get() = fetchPlayState.infoBundleFlow

    @UnsafeEpisodeSessionApi
    private val subjectEpisodeInfoBundleLoadErrorFlow = fetchPlayState.infoLoadErrorFlow
        .filterNotNull()
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), null)

    @UnsafeEpisodeSessionApi
    protected val subjectCollectionFlow =
        subjectEpisodeInfoBundleFlow.filterNotNull().map { it.subjectCollectionInfo }
            .distinctUntilChanged()

    @UnsafeEpisodeSessionApi
    private val subjectInfoFlow = subjectCollectionFlow.map { it.subjectInfo }.distinctUntilChanged()

    @UnsafeEpisodeSessionApi
    private val episodeCollectionFlow = subjectEpisodeInfoBundleFlow.map { it?.episodeCollectionInfo }
        .distinctUntilChanged()

    protected val episodeCollectionsFlow = episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
        .shareInBackground()

    @UnsafeEpisodeSessionApi
    private val episodeInfoFlow = episodeCollectionFlow.map { it?.episodeInfo }.distinctUntilChanged()

    val playerControllerState = PlayerControllerState(ControllerVisibility.Invisible)
    private val mediaSourceInfoProvider: MediaSourceInfoProvider = MediaSourceInfoProvider(
        getSourceInfoFlow = { mediaSourceManager.infoFlowByMediaSourceId(it) },
    )

    val cacheProgressInfoFlow = CacheProgressProvider(
        player, backgroundScope,
        prefetchProgress = fetchPlayState.playerSession.prefetchController.prefetchProgress,
    ).cacheProgressInfoFlow

    @OptIn(UnsafeEpisodeSessionApi::class)
    val videoStatisticsFlow: Flow<VideoStatistics> = VideoStatisticsCollector(
        fetchPlayState.mediaSelectorFlow
            .filterNotNull(),
        fetchPlayState.playerSession.videoLoadingState,
        player,
        mediaSourceInfoProvider,
        mediaSourceLoading = fetchPlayState.episodeSessionFlow.flatMapLatest { it.mediaSourceLoadingFlow },
        backgroundScope,
    ).videoStatisticsFlow

    val videoScaffoldConfig: VideoScaffoldConfig by settingsRepository.videoScaffoldConfig
        .flow.produceState(VideoScaffoldConfig.Default)

    val playbackSpeedRange: ClosedFloatingPointRange<Float>
        get() = videoScaffoldConfig.minPlaybackSpeed..videoScaffoldConfig.maxPlaybackSpeed

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeedOverride.value = speed
        launchInBackground {
            if (settingsRepository.videoScaffoldConfig.flow.first().rememberPlaybackSpeed) {
                settingsRepository.videoScaffoldConfig.update {
                    copy(playbackSpeed = speed)
                }
            }
        }
    }

    var desktopAlwaysOnTopSetByPlayer: Boolean = false

    val playerVolumeFlow: Flow<VideoScaffoldConfig.PlayerVolume> =
        settingsRepository.videoScaffoldConfig.flow.map { it.playerVolume }

    private val selfInfoFlow = SelfInfoStateProducer(koin = getKoin()).flow

    @OptIn(UnsafeEpisodeSessionApi::class)
    protected val recommendationsFlow = subjectInfoFlow.map { getSubjectRecommendations(it.subjectId) }
        .shareTransparentlyIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeDetailsState: EpisodeDetailsState = run {
        EpisodeDetailsState(
            subjectInfo = subjectInfoFlow.produceState(SubjectInfo.Empty),
            airingLabelState = AiringLabelState(
                subjectCollectionFlow.map { it.airingInfo }.produceState(null),
                subjectCollectionFlow.map {
                    SubjectProgressInfo.compute(it.subjectInfo, it.episodes, getCurrentDate(), it.recurrence)
                }
                    .produceState(null),
            ),
            recommendations = recommendationsFlow.produceState(emptyList()),
            subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope),
        )
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeGroups = episodeCollectionsFlow.map { episodes ->
        episodes.chunked(100).mapIndexed { groupIndex, chunk ->
            val startItemIndex = groupIndex * 100
            val startEp = groupIndex * 100 + 1
            val endEp = startEp + chunk.size - 1
            PaginatedGroup(
                title = "第 $startEp-$endEp 话",
                items = chunk,
                startIndex = startItemIndex,
                groupIndex = groupIndex,
            )
        }
    }.produceState(emptyList())

    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeCarouselState: EpisodeCarouselState = run {
        val episodeCacheStatusListState by episodeCollectionsFlow.flatMapLatest { list ->
            if (list.isEmpty()) {
                return@flatMapLatest flowOfEmptyList()
            }
            combine(
                list.map { collection ->
                    downloadManager.downloadStatusForEpisode(subjectId, collection.episodeId).map {
                        collection.episodeId to it
                    }
                },
            ) {
                it.toList()
            }
        }.produceState(emptyList())

        val playProgressByEpisodeId by episodeCollectionsFlow
            .map { list -> list.map { it.episodeId } }
            .distinctUntilChanged()
            .flatMapLatest { episodeIds -> episodePlayHistoryRepository.flowByEpisodeIds(episodeIds) }
            .map { it.playProgressByEpisodeId() }
            .produceState(emptyMap())

        val collectionButtonEnabled = MutableStateFlow(false)
        EpisodeCarouselState(
            episodes = episodeCollectionsFlow.produceState(emptyList()),
            playProgress = { playProgressByEpisodeId[it.episodeId] },
            playingEpisode = episodeIdFlow.combine(episodeCollectionsFlow) { id, collections ->
                collections.firstOrNull { it.episodeId == id }
            }.produceState(null),
            cacheStatus = {
                episodeCacheStatusListState.firstOrNull { status ->
                    status.first == it.episodeInfo.episodeId
                }?.second ?: EpisodeCacheStatus.NotCached
            },
            onSelect = {
                launchInBackground {
                    switchEpisode(it.episodeInfo.episodeId)
                }
            },
            onChangeCollectionType = { episode, it ->
                collectionButtonEnabled.value = false
                launchInBackground {
                    try {
                        episodeCollectionRepository.setEpisodeCollectionType(
                            subjectId,
                            episodeId = episode.episodeInfo.episodeId,
                            collectionType = it,
                        )
                    } finally {
                        collectionButtonEnabled.value = true
                    }
                }
            },
            backgroundScope = backgroundScope,
            groupsState = episodeGroups,
        )
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    val editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState =
        EditableSubjectCollectionTypeState(
            selfCollectionTypeFlow = subjectCollectionFlow
                .map { it.collectionType },
            hasAnyUnwatched = {
                val collections =
                    episodeCollectionsFlow.firstOrNull() ?: return@EditableSubjectCollectionTypeState true
                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = { setSubjectCollectionTypeOrDeleteUseCase(subjectId, it) },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(subjectId)
            },
            backgroundScope,
        )

    var isFullscreen: Boolean by mutableStateOf(initialIsFullscreen)
    var sidebarVisible: Boolean by mutableStateOf(true)
    val commentLazyGirdState: LazyGridState = LazyGridState()

    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeSelectorState: EpisodeSelectorState = EpisodeSelectorState(
        itemsFlow = episodeCollectionsFlow.combine(subjectCollectionFlow) { list, subject ->
            list.map {
                it.toPresentation(subject.recurrence)
            }
        },
        onSelect = {
            launchInBackground {
                switchEpisode(it.episodeId)
            }
        },
        currentEpisodeId = episodeIdFlow,
        parentCoroutineContext = backgroundScope.coroutineContext,
    )

    private val commentStateRestarter = FlowRestarter()
    private val commentLoadFailureChannel = Channel<Throwable>(Channel.BUFFERED)

    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeCommentsPager = episodeIdFlow
        .restartable(commentStateRestarter)
        .flatMapLatest { episodeId ->
            episodeCommentRepository.subjectEpisodeCommentsPager(
                episodeId.toLong(),

                onBangumiUnavailable = {
                    commentLoadFailureChannel.trySend(
                        RepositoryServiceUnavailableException("Bangumi episode comments unavailable"),
                    )
                },
            )
        }.cachedIn(backgroundScope)

    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeCommentState: CommentState = CommentState(
        list = episodeCommentsPager.map { page -> page.map { it.parseToUIComment() } }.cachedIn(backgroundScope),
        countState = stateOf(null),
        onSubmitCommentReaction = { comment, value, selected ->

            if (comment.source == UICommentSource.WYNIME) {
                episodeCommentRepository.submitReaction(

                    episodeId = comment.episodeId ?: episodeIdFlow.first().toLong(),
                    commentId = comment.sourceCommentId,
                    value = value,
                    selected = selected,
                )
            }
        },
        backgroundScope = backgroundScope,
        commentLoadFailures = commentLoadFailureChannel.receiveAsFlow(),
        onSubmitCommentVote = { comment, vote ->

            if (comment.source == UICommentSource.WYNIME) {
                episodeCommentRepository.submitVote(
                    episodeId = comment.episodeId ?: episodeIdFlow.first().toLong(),
                    commentId = comment.sourceCommentId,
                    vote = vote?.toCommentVoteValue(),
                )
            }
        },
    )

    @OptIn(UnsafeEpisodeSessionApi::class)
    val commentReportState: CommentReportState = CommentReportState(
        onSubmitReport = { comment, reason, detail ->
            commentReportService.createReport(
                targetType = CommentReportTargetType.EPISODE_COMMENT,
                targetId = comment.sourceCommentId,
                reason = reason.toDataReason(),
                commentAuthorId = comment.author?.id,
                detail = detail.takeIf { it.isNotEmpty() },
                contentSnapshot = comment.reportSnapshotText(),
                subjectId = subjectId.toLong(),

                episodeId = comment.episodeId ?: episodeIdFlow.first().toLong(),
            )
        },
        backgroundScope = backgroundScope,
    )

    @OptIn(UnsafeEpisodeSessionApi::class)
    val commentEditorState: CommentEditorState = CommentEditorState(
        showExpandEditCommentButton = true,
        initialEditExpanded = false,
        panelTitle = subjectInfoFlow
            .combine(episodeInfoFlow) { sub, epi -> "${sub.displayName} ${epi?.renderEpisodeEp()}" }
            .produceState(null),
        stickers = flowOf(BangumiCommentSticker.map { EditCommentSticker(it.first, it.second) })
            .produceState(emptyList()),
        richTextRenderer = { text ->
            withContext(Dispatchers.Default) {
                with(CommentMapperContext) { parseBBCode(text) }
            }
        },
        onSend = { context, content -> postCommentUseCase(context, content) },
        backgroundScope = backgroundScope,
    )

    private val combinedChaptersFlow: Flow<List<Chapter>> = player.chapters ?: flowOf(emptyList())

    val progressChaptersFlow: Flow<List<Chapter>> = combinedChaptersFlow

    val playerSkipOpEdState: PlayerSkipOpEdState = PlayerSkipOpEdState(
        chapters = combinedChaptersFlow.produceState(emptyList()),
        onSkip = {
            launchInBackground(Dispatchers.Main) {
                player.seekTo(it)
            }
        },
        videoLength = player.mediaProperties.mapNotNull { it?.durationMillis?.milliseconds }
            .produceState(0.milliseconds),
    )

    val pageState = fetchPlayState.episodeSessionFlow.transformLatest { episodeSession ->
        logger.info { "Switching to new episodeSession ${episodeSession.episodeId}" }
        coroutineScope {
            emitAll(createPageStateFlow(episodeSession))
            awaitCancellation()
        }
    }.stateIn(backgroundScope, started = SharingStarted.WhileSubscribed(5_000), null)

    private fun CoroutineScope.createPageStateFlow(episodeSession: EpisodeSession): Flow<EpisodePageState> {

        episodeSession.fetchSelectFlow.flatMapLatest {
            it?.mediaFetchSession?.cumulativeResults ?: flowOfEmptyList()
        }.launchIn(this)

        val filteredSourceResults = MediaSourceResultsFilterer(
            results = episodeSession.fetchSelectFlow.map {
                it?.mediaFetchSession?.mediaSourceResults ?: emptyList()
            },
            settings = settingsRepository.mediaSelectorSettings.flow,
            flowScope = this,
        ).filteredSourceResults
            .shareIn(this, started = SharingStarted.Lazily, replay = 1)

        val mediaSelectorSummaryStateProducer = MediaSelectorSummaryStateProducer(
            episodeSession.fetchSelectFlow.mapNotNull { it?.mediaSelector }
                .flatMapLatest { it.selectedMaybeExcludedMediaFlow }
                .onStart { emit(null) },
            filteredSourceResults,
            getMediaSelectorSettings(),
            getMediaSourceInstances.getAsMediaSourceInfoWithId(),
        ).flow.stateIn(
            this,
            started = SharingStarted.Lazily,
            initialValue = MediaSelectorSummary.AutoSelecting(listOf(), estimate = 10.seconds),
        )

        val selectedMediaFlow =
            episodeSession.fetchSelectFlow.flatMapLatest { it?.mediaSelector?.selected ?: flowOfNull() }
        return com.wynime.utils.coroutines.flows.combine(
            selfInfoFlow,
            episodeSession.infoBundleFlow.distinctUntilChanged().onStart { emit(null) },
            episodeSession.infoLoadErrorStateFlow,
            episodeSession.fetchSelectFlow,
            episodeSession.fetchSelectFlow.map { fetchSelect ->
                if (fetchSelect != null) {
                    MediaSelectorState(
                        fetchSelect.mediaSelector,
                        filteredSourceResults,
                        mediaSourceInfoProvider,
                        getPreferredWebMediaSource(subjectId),
                        backgroundScope,
                        webSessionManager,
                    )
                } else {

                    @OptIn(TestOnly::class)
                    createTestMediaSelectorState(backgroundScope)
                }
            },
            mediaSelectorSummaryStateProducer,
            combine(selectedMediaFlow, player.mediaData) { selectedMedia, mediaData ->
                MediaShareData.from(selectedMedia, mediaData)
            },
        ) { authState, subjectEpisodeBundle, subjectLoadError, fetchSelect, mediaSelectorState, mediaSelectorSummary, shareData ->

            val (subject, episode) = if (subjectEpisodeBundle == null) {
                SubjectPresentation.Placeholder to EpisodePresentation.Placeholder
            } else {
                Pair(
                    subjectEpisodeBundle.subjectInfo.toPresentation(),
                    subjectEpisodeBundle.episodeCollectionInfo.toPresentation(subjectEpisodeBundle.subjectCollectionInfo.recurrence),
                )
            }

            if (subjectLoadError != null) {
                logger.warn { "InfoBundle load error: $subjectLoadError" }
            }

            fun getLoadError(): EpisodePageLoadError? {

                subjectLoadError?.let {
                    return EpisodePageLoadError.SubjectError(subjectLoadError)
                }
                return null
            }

            EpisodePageState(
                selfInfo = authState,
                mediaSelectorState = mediaSelectorState,
                subjectPresentation = subject,
                episodePresentation = episode,
                isLoading = subjectEpisodeBundle == null,
                loadError = getLoadError(),
                playingEpisodeSummary = if (subjectEpisodeBundle == null) {
                    null
                } else {
                    PlayingEpisodeSummary(
                        episodeSort = subjectEpisodeBundle.episodeInfo.sort,
                        episodeName = subjectEpisodeBundle.episodeInfo.displayName,
                        subjectName = subjectEpisodeBundle.subjectInfo.displayName,
                        episodeOriginalName = subjectEpisodeBundle.episodeInfo.nameOrNameCn,
                        subjectOriginalName = subjectEpisodeBundle.subjectInfo.nameOrNameCn,
                        subjectTags = listOf(),
                        subjectCoverUrl = subjectEpisodeBundle.subjectInfo.imageLarge,
                        rating = subjectEpisodeBundle.subjectInfo.ratingInfo,
                        selfRatingInfo = subjectEpisodeBundle.subjectCollectionInfo.selfRatingInfo,
                    )
                },
                mediaSelectorSummary = mediaSelectorSummary,

                fetchRequest = fetchSelect?.mediaFetchSession?.latestRequest?.first()?.let { request ->
                    subjectEpisodeBundle?.episodeInfo?.let { request.withCurrentEpisode(it) } ?: request
                },
                shareData = shareData,
            )
        }
    }

    suspend fun switchEpisode(episodeId: Int) {

        backgroundScope.launch {
            fetchPlayState.switchEpisode(episodeId)
        }.join()
    }

    fun savePlayerVolume(volume: Float, mute: Boolean) {
        launchInBackground {
            tasker.invoke {
                delay(200)
                settingsRepository.videoScaffoldConfig
                    .update { copy(playerVolume = VideoScaffoldConfig.PlayerVolume(volume, mute)) }
            }
        }
    }

    fun refreshFetch() {
        launchInBackground {

            fetchPlayState.episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }
                .mapNotNull { it?.mediaFetchSession }
                .firstOrNull()
                ?.restartAll()
        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    fun onClickSkipOpEd(currentPositionMillis: Long) {
        val skipDuration = videoScaffoldConfig.opEdSkipDuration
        player.skip(skipDuration.inWholeMilliseconds)
    }

    fun restartSource(instanceId: String) {
        launchInBackground {
            val result = fetchPlayState.episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }
                .mapNotNull { it?.mediaFetchSession }
                .firstOrNull()
                ?.mediaSourceResults
                ?.find { it.instanceId == instanceId }
                ?: return@launchInBackground
            result.restart()
        }
    }

    fun playDroppedFile(file: SystemPath) {
        launchInBackground {
            val session = fetchPlayState.episodeSessionFlow.value
            val mediaSelector = fetchPlayState.episodeSessionFlow
                .mapLatest { current ->
                    if (current !== session) return@mapLatest null
                    current.fetchSelectFlow.filterNotNull().first().mediaSelector
                }
                .first()
                ?: return@launchInBackground
            logger.info { "Playing dropped file: $file" }
            mediaSelector.selectTemporarily(DroppedFileMedia.create(file))
        }
    }

    fun onUIReady() {
        fetchPlayState.onUIReady()
    }

    init {
        launchInBackground {
            val defaultMode = settingsRepository.videoScaffoldConfig.flow
                .first()
                .videoEnhancementDefaultMode
            videoEnhancement?.setMode(
                when (defaultMode) {
                    VideoEnhancementDefaultMode.OFF -> VideoEnhancementMode.OFF
                    VideoEnhancementDefaultMode.PERFORMANCE -> VideoEnhancementMode.PERFORMANCE
                    VideoEnhancementDefaultMode.QUALITY -> VideoEnhancementMode.QUALITY
                },
            )
        }

        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoSkipOpEd }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { enabled ->
                    val prefetchController = fetchPlayState.playerSession.prefetchController
                    if (!enabled) {
                        prefetchController.setPrefetchRequest(null)
                        return@collectLatest
                    }

                    val positionSamples = player.features[PlaybackSpeed]?.let { playbackSpeed ->
                        playbackSpeed.valueFlow
                            .onStart { emit(playbackSpeed.value) }
                            .distinctUntilChanged()
                            .flatMapLatest { speed ->
                                player.currentPositionMillis.sampleWithInitial(
                                    opEdAutoSkipSampleIntervalMillis(speed),
                                )
                            }
                    } ?: player.currentPositionMillis.sampleWithInitial(
                        OP_ED_AUTO_SKIP_BASE_SAMPLE_INTERVAL_MILLIS,
                    )
                    @OptIn(UnsafeEpisodeSessionApi::class)
                    combine(
                        positionSamples,
                        episodeIdFlow,
                        episodeCollectionsFlow,
                    ) { pos, id, collections ->

                        val skipAllowed = !(collections.size > 1 && collections.getOrNull(0)?.episodeId == id) &&
                                isAutoSkipOpEdAllowed
                        if (skipAllowed) {
                            playerSkipOpEdState.update(pos)

                            prefetchController.setPrefetchRequest(playerSkipOpEdState.prefetchRequest)
                        } else {
                            prefetchController.setPrefetchRequest(null)
                        }
                    }.collect()
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        videoEnhancement?.close()
        webSessionManager.cancelAutoSolves()
        backgroundScope.launch(NonCancellable + CoroutineName("EpisodeViewModel#onCleared")) {
            fetchPlayState.onClose()
        }
    }

    override fun getKoin(): Koin = koin

    fun updateFetchRequest(request: MediaFetchRequest) {
        launchInBackground {
            fetchPlayState.episodeSessionFlow
                .firstOrNull()
                ?.fetchSelectFlow
                ?.firstOrNull()
                ?.mediaFetchSession
                ?.setFetchRequest(request)
        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    fun retryLoad(error: EpisodePageLoadError) {
        launchInBackground {
            when (error) {
                is EpisodePageLoadError.SeriesError -> {
                    fetchPlayState.restartLoad()
                }

                is EpisodePageLoadError.SubjectError -> {
                    fetchPlayState.restartLoad()
                }
            }
        }
    }

    private suspend fun MediampPlayer.applyCustomOptions() {
        val config = try {
            settingsRepository.playerKernelConfig.flow.map { it.mpvOptions }.first()
        } catch (e: Exception) {
            if (e !is CancellationException) logger.warn(e) { "Failed to get custom mpv options." }
            return
        }

        applyMpvOptions(parseMpvOptions(config))
    }
}

private fun MediaFetchRequest.withCurrentEpisode(episode: EpisodeInfo): MediaFetchRequest {
    if (episodeId == episode.episodeId.toString()) return this
    return copy(
        episodeId = episode.episodeId.toString(),
        episodeSort = episode.sort,
        episodeName = episode.displayName,
        episodeEp = episode.ep,
    )
}
