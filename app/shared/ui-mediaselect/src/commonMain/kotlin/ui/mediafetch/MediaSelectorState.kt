package com.wynime.app.ui.mediafetch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.domain.media.fetch.isFailedOrAbandoned
import com.wynime.app.domain.media.fetch.isWorking
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.MediaPreferenceItem
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.isPerfectMatch
import com.wynime.app.domain.mediasource.web.captcha.SolveOutcome
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.domain.mediasource.web.captcha.createTestWebSessionManager
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.app.ui.foundation.rememberBackgroundScope
import com.wynime.app.ui.mediaselect.selector.WebSource
import com.wynime.app.ui.mediaselect.selector.WebSourceChannel
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.source.plugin.api.SourceResultStatus
import com.wynime.utils.coroutines.flows.flowOfEmptyList
import com.wynime.utils.platform.annotations.TestOnly

@Composable
fun rememberMediaSelectorState(
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    filteredResults: Flow<List<MediaSourceFetchResult>>,
    mediaSelector: () -> MediaSelector,
): MediaSelectorState {
    val scope = rememberBackgroundScope()
    val webSessionManager = remember { GlobalKoin.get<WebSessionManager>() }
    val selector by remember {
        derivedStateOf(mediaSelector)
    }
    return remember {
        MediaSelectorState(
            selector,
            filteredResults,
            mediaSourceInfoProvider,
            flowOf(null),
            scope.backgroundScope,
            webSessionManager,
        )
    }
}

@Stable
class MediaPreferenceItemState<T : Any>(
    @PublishedApi internal val item: MediaPreferenceItem<T>,
    backgroundScope: CoroutineScope,
) {
    data class Presentation<T : Any>(
        val available: List<T>,
        val finalSelected: T?,
        val isWorking: Boolean = false,
        val isPlaceholder: Boolean = false,
    ) {
        companion object {
            private val Placeholder = Presentation(emptyList(), null, isWorking = false, isPlaceholder = true)

            @Suppress("UNCHECKED_CAST")
            fun <T : Any> placeholder(): Presentation<T> = Placeholder as Presentation<T>
        }
    }

    val presentationFlow = combine(
        item.available,
        item.finalSelected,
        transform = ::Presentation,
    ).stateIn(
        backgroundScope, started = SharingStarted.WhileSubscribed(),
        Presentation.placeholder(),
    )

    suspend fun prefer(value: T) {
        item.prefer(value)
    }

    suspend fun removePreference() {
        item.removePreference()
    }
}

suspend fun <T : Any> MediaPreferenceItemState<T>.preferOrRemove(value: T?) {
    return if (value == null || value == presentationFlow.value.finalSelected) {
        removePreference()
    } else {
        prefer(value)
    }
}

@Stable
class MediaSelectorState(
    private val mediaSelector: MediaSelector,
    private val mediaSourceFetchResults: Flow<List<MediaSourceFetchResult>>,
    val mediaSourceInfoProvider: MediaSourceInfoProvider,
    private val preferredWebMediaSource: Flow<String?>,
    private val backgroundScope: CoroutineScope,
    private val webSessionManager: WebSessionManager,
) {
    @Immutable
    data class Presentation(
        val filteredCandidates: List<MaybeExcludedMedia>,
        val preferredCandidates: List<Media>,
        val groupedMediaListIncluded: List<MediaGroup>,
        val groupedMediaListExcluded: List<MediaGroup>,
        val selected: Media?,
        val alliance: MediaPreferenceItemState.Presentation<String>,
        val resolution: MediaPreferenceItemState.Presentation<String>,
        val subtitleLanguageId: MediaPreferenceItemState.Presentation<String>,
        val mediaSource: MediaPreferenceItemState.Presentation<String>,

        val webSources: List<WebSource>,
        val selectedWebSource: WebSource?,
        val selectedWebSourceChannel: WebSourceChannel?,
        val isPlaceholder: Boolean = false,
    )

    private val groupStates: SnapshotStateMap<MediaGroupId, MediaGroupState> = SnapshotStateMap()
    private val resolvingCaptchaInstanceIds = MutableStateFlow<Set<String>>(emptySet())

    val sourceResultsPresentationFlow = MediaSourceResultListPresenter(
        resultListFlow = mediaSourceFetchResults,
        preferredWebMediaSourceIdFlow = preferredWebMediaSource,
    ).presentationFlow.map(::MediaSourceResultListPresentation)
        .stateIn(
            backgroundScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = MediaSourceResultListPresentation.Empty,
        )

    fun getGroupState(groupId: MediaGroupId): MediaGroupState {
        return groupStates.getOrPut(groupId) {
            MediaGroupState(groupId)
        }
    }

    val alliance: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.alliance, backgroundScope)
    val resolution: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.resolution, backgroundScope)
    val subtitleLanguageId: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.subtitleLanguageId, backgroundScope)
    val mediaSource: MediaPreferenceItemState<String> =
        MediaPreferenceItemState(mediaSelector.mediaSourceId, backgroundScope)

    val presentationFlow = com.wynime.utils.coroutines.flows.combine(
        mediaSelector.filteredCandidates,
        mediaSelector.preferredCandidates,
        mediaSelector.selected,
        alliance.presentationFlow,
        resolution.presentationFlow,
        subtitleLanguageId.presentationFlow,
        mediaSource.presentationFlow,
        createWebSourcesFlow(),
    ) { filteredCandidates, preferredCandidates, selected, alliance, resolution, subtitleLanguageId, mediaSource, webSources ->

        val visibleCandidates = filteredCandidates.filterNot { it.exclusionReason is MediaExclusionReason.EpisodeMismatch }
        val visiblePreferred = preferredCandidates.filterNot { it.exclusionReason is MediaExclusionReason.EpisodeMismatch }

        val (groupsExcluded, groupsIncluded) = MediaGrouper.buildGroups(visiblePreferred).partition { it.isExcluded }
        Presentation(
            visibleCandidates,
            visiblePreferred.mapNotNull { it.result },
            groupsIncluded,
            groupsExcluded,
            selected,
            alliance, resolution, subtitleLanguageId, mediaSource,
            webSources,
            selectedWebSource = webSources.find { source -> source.channels.any { it.original == selected } },
            selectedWebSourceChannel = webSources.firstNotNullOfOrNull { source -> source.channels.find { it.original == selected } },
        )
    }.stateIn(
        backgroundScope,
        started = SharingStarted.WhileSubscribed(),
        Presentation(
            emptyList(), emptyList(), emptyList(), emptyList(), null,
            alliance = MediaPreferenceItemState.Presentation.placeholder(),
            resolution = MediaPreferenceItemState.Presentation.placeholder(),
            subtitleLanguageId = MediaPreferenceItemState.Presentation.placeholder(),
            mediaSource = MediaPreferenceItemState.Presentation.placeholder(),
            webSources = emptyList(),
            selectedWebSource = null,
            selectedWebSourceChannel = null,
            isPlaceholder = true,
        ),
    )

    private fun createWebSourcesFlow(): Flow<List<WebSource>> {

        var isFirstCollect = true

        val sortedResultsFlow = mediaSourceFetchResults.flatMapLatest { results ->
            if (results.isEmpty()) return@flatMapLatest flowOfEmptyList()

            val sorted = results.filter { it.kind == MediaSourceKind.WEB }

            combine(results.map { it.state }) { states ->
                sorted.sortedBy {
                    val state = states.getOrNull(results.indexOf(it))
                        ?: return@sortedBy 0
                    if (state is MediaSourceFetchState.Failed) {
                        1
                    } else {
                        -1
                    }
                }
            }
        }

        return combine(
            sortedResultsFlow.distinctUntilChanged(),
            mediaSelector.filteredCandidates,
            resolvingCaptchaInstanceIds,
        ) { sources, mediaList, resolvingCaptchaInstanceIds ->
            Triple(sources, mediaList, resolvingCaptchaInstanceIds)
        }.flatMapLatest { (sources, allMediaList, resolvingCaptchaInstanceIds) ->
            val showWebSources = sources.map { source ->

                val myMediaList = visibleSourceMedia(allMediaList, source.mediaSourceId).asSequence()

                createWebSourceFlow(
                    source,
                    myMediaList,
                    delayToOvercomeCacheIssue = isFirstCollect,
                    resolvingCaptchaInstanceIds = resolvingCaptchaInstanceIds,
                ).also {
                    isFirstCollect = false
                }
            }
            if (showWebSources.isEmpty()) {
                flowOfEmptyList()
            } else {
                combine(
                    showWebSources,
                ) {
                    it.filterNotNull()
                }
            }
        }
    }

    private fun createWebSourceFlow(
        source: MediaSourceFetchResult,
        myMediaList: Sequence<Media>,
        delayToOvercomeCacheIssue: Boolean,
        resolvingCaptchaInstanceIds: Set<String>,
    ) = source.state.combine(preferredWebMediaSource) { a, b -> a to b }.map { (state, preferred) ->

        val channels = myMediaList.distinctBy { it.properties.alliance }.map { media ->
            WebSourceChannel(media.properties.alliance, original = media)
        }.toList()
        val captchaRequest = (state as? MediaSourceFetchState.CaptchaRequired)?.request
        val rateLimitedUntil = (state as? MediaSourceFetchState.RateLimited)?.retryAt

        when {
            state is MediaSourceFetchState.Disabled -> {

                null
            }

            channels.isEmpty() && state is MediaSourceFetchState.Succeed -> {

                if (delayToOvercomeCacheIssue) {
                    delay(1000)
                }
                null
            }

            else -> {
                WebSource(
                    instanceId = source.instanceId,
                    mediaSourceId = source.mediaSourceId,
                    iconUrl = source.sourceInfo.iconUrl ?: "",
                    name = source.sourceInfo.displayName,
                    channels = channels,
                    isLoading = state.isWorking,
                    isError = state.isFailedOrAbandoned || state is MediaSourceFetchState.NoMatch,
                    isPreferred = source.mediaSourceId == preferred,
                    captchaRequest = captchaRequest,
                    isResolvingCaptcha = source.instanceId in resolvingCaptchaInstanceIds,
                    rateLimitedUntilMillis = rateLimitedUntil,
                    isCaptchaSupported = webSessionManager.isInteractiveSupported,
                    isNoMatch = state is MediaSourceFetchState.NoMatch,
                    sourceStatus = when (state) {
                        is MediaSourceFetchState.NoMatch -> state.diagnostics.responseCategory
                        is MediaSourceFetchState.Failed -> state.diagnostics?.responseCategory
                        is MediaSourceFetchState.CaptchaRequired -> SourceResultStatus.BLOCKED_BY_CHALLENGE
                        is MediaSourceFetchState.RateLimited -> SourceResultStatus.HTTP_ERROR
                        else -> null
                    },
                )
            }
        }
    }

    fun select(candidate: Media) {
        backgroundScope.launch {
            mediaSelector.select(candidate)
        }
    }

    fun removePreferencesUntilFirstCandidate() {
        backgroundScope.launch {
            mediaSelector.removePreferencesUntilFirstCandidate()
        }
    }

    suspend fun resolveCaptcha(instanceId: String): Boolean {
        val source = presentationFlow.value.webSources.find { it.instanceId == instanceId } ?: return false
        return resolveCaptcha(source)
    }

    suspend fun resolveCaptcha(source: WebSource): Boolean {
        val request = source.captchaRequest ?: return false
        resolvingCaptchaInstanceIds.value = resolvingCaptchaInstanceIds.value + source.instanceId
        return try {
            webSessionManager.solve(request, interactive = true) == SolveOutcome.Solved
        } finally {
            resolvingCaptchaInstanceIds.value = resolvingCaptchaInstanceIds.value - source.instanceId
        }
    }
}

internal fun visibleSourceMedia(
    candidates: List<MaybeExcludedMedia>,
    mediaSourceId: String,
): List<Media> {
    val sourceCandidates = candidates
        .filter { it.result?.mediaSourceId == mediaSourceId }
    val exactMedia = sourceCandidates
        .filter { it.isPerfectMatch() }
        .mapNotNull { it.result }
    return (exactMedia.ifEmpty { sourceCandidates.mapNotNull { it.result } })
        .distinctBy { it.mediaId }
}

@Stable
class MediaGroupState(
    val groupId: MediaGroupId,
) {
    var selectedItem: Media? by mutableStateOf(null)
}

@Composable
@TestOnly
fun rememberTestMediaSelectorState(): MediaSelectorState {
    val backgroundScope = rememberBackgroundScope()
    return remember(backgroundScope) { createTestMediaSelectorState(backgroundScope.backgroundScope) }
}

@TestOnly
fun createTestMediaSelectorState(backgroundScope: CoroutineScope) =
    MediaSelectorState(
        DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
            mediaListNotCached = MutableStateFlow(TestMediaList),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(MediaPreference.Empty),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
        ),
        mediaSourceFetchResults = createTestMediaSourceResultsFilterer(backgroundScope).filteredSourceResults,
        createTestMediaSourceInfoProvider(),
        preferredWebMediaSource = flowOf(null),
        backgroundScope,
        createTestWebSessionManager(backgroundScope),
    )
