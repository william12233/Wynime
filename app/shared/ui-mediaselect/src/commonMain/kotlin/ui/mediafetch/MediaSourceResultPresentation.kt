package com.wynime.app.ui.mediafetch

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.domain.media.fetch.MediaSourceResultsFilterer
import com.wynime.app.domain.media.fetch.isCaptchaRequired
import com.wynime.app.domain.media.fetch.isDisabled
import com.wynime.app.domain.media.fetch.isFailedOrAbandoned
import com.wynime.app.domain.media.fetch.isRateLimited
import com.wynime.app.domain.media.fetch.isWorking
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.app.domain.mediasource.web.WebCaptchaKind
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.source.plugin.api.SourceResultStatus
import com.wynime.utils.coroutines.flows.flowOfEmptyList
import com.wynime.utils.platform.annotations.TestOnly

internal fun List<Media>.countPlaybackLines(): Int = distinctBy { it.properties.alliance }.size

@Stable
data class MediaSourceResultPresentation(
    val instanceId: String,
    val mediaSourceId: String,
    val state: MediaSourceFetchState,
    val info: MediaSourceInfo,
    val kind: MediaSourceKind,
    val totalCount: Int,
    val isPreferred: Boolean,
) {
    val isWorking: Boolean get() = state.isWorking
    val isDisabled: Boolean get() = state.isDisabled
    val isFailedOrAbandoned: Boolean get() = state.isFailedOrAbandoned
    val isNoMatch: Boolean get() = state is MediaSourceFetchState.NoMatch
    val sourceStatus: SourceResultStatus?
        get() = when (val value = state) {
            is MediaSourceFetchState.NoMatch -> value.diagnostics.responseCategory
            is MediaSourceFetchState.Failed -> value.diagnostics?.responseCategory
            is MediaSourceFetchState.CaptchaRequired -> SourceResultStatus.BLOCKED_BY_CHALLENGE
            is MediaSourceFetchState.RateLimited -> SourceResultStatus.HTTP_ERROR
            else -> null
        }
    val isCaptchaRequired: Boolean get() = state.isCaptchaRequired
    val isRateLimited: Boolean get() = state.isRateLimited
    val rateLimitedUntilMillis: Long? get() = (state as? MediaSourceFetchState.RateLimited)?.retryAt
    val captchaRequest: SolveRequest? get() = (state as? MediaSourceFetchState.CaptchaRequired)?.request
    val captchaKind: WebCaptchaKind? get() = captchaRequest?.kind
}

@Immutable
data class MediaSourceResultListPresentation(
    val list: List<MediaSourceResultPresentation>,
) {
    val anyLoading: Boolean = list.any { it.isWorking }
    val webSources: List<MediaSourceResultPresentation> = list.filter { it.kind == MediaSourceKind.WEB }
    val enabledSourceCount: Int = list.count { !it.isDisabled && it.kind != MediaSourceKind.LocalCache }
    val totalSourceCount: Int = list.count { it.kind != MediaSourceKind.LocalCache }

    companion object {
        val Empty = MediaSourceResultListPresentation(emptyList())
    }
}

class MediaSourceResultListPresenter(
    resultListFlow: Flow<List<MediaSourceFetchResult>>,
    preferredWebMediaSourceIdFlow: Flow<String?> = flowOf(null),
    includedMediaFlow: Flow<List<Media>>? = null,
) {
    val presentationFlow: Flow<List<MediaSourceResultPresentation>> = resultListFlow
        .combine(preferredWebMediaSourceIdFlow) { list, preferredWebMediaSourceId ->
            Pair(list, preferredWebMediaSourceId)
        }
        .flatMapLatest { (list, preferred) ->
            val flows = list.map { source ->
                val countFlow = includedMediaFlow
                    ?.map { included ->
                        included.filter { it.mediaSourceId == source.mediaSourceId }.countPlaybackLines()
                    }
                    ?: source.results.map { it.countPlaybackLines() }
                combine(source.state, countFlow) { state, count ->
                    source.toPresentation(
                        state,
                        count,
                        source.mediaSourceId == preferred,
                    )
                }
            }
            if (flows.isEmpty()) {
                flowOfEmptyList()
            } else {
                combine(
                    flows,
                ) {
                    it.toList()
                }
            }
        }

    private fun MediaSourceFetchResult.toPresentation(
        state: MediaSourceFetchState,
        totalCount: Int,
        isPreferred: Boolean,
    ): MediaSourceResultPresentation =
        MediaSourceResultPresentation(
            instanceId = instanceId,
            mediaSourceId = mediaSourceId,
            state = state,
            info = sourceInfo,
            kind = kind,
            totalCount = totalCount,
            isPreferred = isPreferred,
        )
}

@TestOnly
fun createTestMediaSourceResultsPresenter(
    flowScope: CoroutineScope,
): MediaSourceResultListPresenter {
    return MediaSourceResultListPresenter(
        createTestMediaSourceResultsFilterer(flowScope).filteredSourceResults,
    )
}

@TestOnly
fun createTestMediaSourceResultsFilterer(
    flowScope: CoroutineScope
) = MediaSourceResultsFilterer(
    results = flowOf(
        listOf(
            TestMediaSourceResult(
                "source1",
                MediaSourceInfo("source1"),
                MediaSourceKind.WEB,
                initialState = MediaSourceFetchState.Working,
                results = TestMediaList,
            ),
            TestMediaSourceResult(
                "source2",
                MediaSourceInfo("source2"),
                MediaSourceKind.WEB,
                initialState = MediaSourceFetchState.Succeed(1),
                results = TestMediaList,
            ),
            TestMediaSourceResult(
                "source3",
                MediaSourceInfo("source3"),
                MediaSourceKind.WEB,
                initialState = MediaSourceFetchState.Disabled,
                results = TestMediaList,
            ),
            TestMediaSourceResult(
                "source4",
                MediaSourceInfo("source4"),
                MediaSourceKind.WEB,
                initialState = MediaSourceFetchState.Failed(IllegalStateException(), 1),
                results = TestMediaList,
            ),
        ),
    ),
    settings = flowOf(MediaSelectorSettings.Default),
    flowScope = flowScope,
)

@TestOnly
val TestMediaSourceResultListPresentation
    get() = MediaSourceResultListPresentation(
        listOf(
            MediaSourceResultPresentation(
                instanceId = "source1",
                mediaSourceId = "source1",
                state = MediaSourceFetchState.Working,
                info = MediaSourceInfo("source1"),
                kind = MediaSourceKind.WEB,
                totalCount = TestMediaList.size,
                isPreferred = false,
            ),
            MediaSourceResultPresentation(
                instanceId = "source2",
                mediaSourceId = "source2",
                state = MediaSourceFetchState.Succeed(1),
                info = MediaSourceInfo("source2"),
                kind = MediaSourceKind.WEB,
                totalCount = TestMediaList.size,
                isPreferred = false,
            ),
            MediaSourceResultPresentation(
                instanceId = "source3",
                mediaSourceId = "source3",
                state = MediaSourceFetchState.Disabled,
                info = MediaSourceInfo("source3"),
                kind = MediaSourceKind.WEB,
                totalCount = TestMediaList.size,
                isPreferred = false,
            ),
            MediaSourceResultPresentation(
                instanceId = "source4",
                mediaSourceId = "source4",
                state = MediaSourceFetchState.Succeed(1),
                info = MediaSourceInfo("source4"),
                kind = MediaSourceKind.WEB,
                totalCount = TestMediaList.size,
                isPreferred = true,
            ),
            MediaSourceResultPresentation(
                instanceId = "source5",
                mediaSourceId = "source5",
                state = MediaSourceFetchState.Succeed(2),
                info = MediaSourceInfo("source5"),
                kind = MediaSourceKind.WEB,
                totalCount = TestMediaList.size,
                isPreferred = false,
            ),
        ),
    )

private class TestMediaSourceResult(
    override val mediaSourceId: String,
    override val sourceInfo: MediaSourceInfo,
    override val kind: MediaSourceKind,
    initialState: MediaSourceFetchState,
    results: List<Media>,
    override val instanceId: String = mediaSourceId,
) : MediaSourceFetchResult {
    override val state: MutableStateFlow<MediaSourceFetchState> = MutableStateFlow(initialState)
    override val results: SharedFlow<List<Media>> = MutableStateFlow(results)
    private val restartCount = atomic(0)

    @OptIn(DelicateCoroutinesApi::class)
    override fun restart() {
        state.value = MediaSourceFetchState.Working
        GlobalScope.launch {
            delay(3000)
            state.value = MediaSourceFetchState.Succeed(restartCount.incrementAndGet())
        }
    }

    override fun enable() {
        if (state.value is MediaSourceFetchState.Disabled) {
            if (restartCount.compareAndSet(0, 1)) {
                state.value = MediaSourceFetchState.Idle
            }
        }
    }
}
