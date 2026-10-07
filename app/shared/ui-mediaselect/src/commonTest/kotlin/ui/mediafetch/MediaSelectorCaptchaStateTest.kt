package com.wynime.app.ui.mediafetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.mediasource.web.PageExpectation
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.app.domain.mediasource.web.WebCaptchaKind
import com.wynime.app.domain.mediasource.web.captcha.createTestWebSessionManager
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class MediaSelectorCaptchaStateTest {
    private fun solveRequest(kind: WebCaptchaKind) = SolveRequest(
        mediaSourceId = "source-1",
        pageUrl = "https://example.com/search",
        kind = kind,
        expectation = PageExpectation.AnyContent,
    )

    @Test
    fun `captcha state is exposed to simple mode presentation`() = runTest {
        val request = solveRequest(WebCaptchaKind.Cloudflare)
        val stateScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val state = createState(
            sourceResults = listOf(
                FakeMediaSourceFetchResult(
                    initialState = MediaSourceFetchState.CaptchaRequired(request, id = 1),
                ),
            ),
            backgroundScope = stateScope,
        )
        try {
            val presentation = state.presentationFlow.first { !it.isPlaceholder }
            val source = presentation.webSources.single()

            assertEquals(request, source.captchaRequest)
            assertEquals(WebCaptchaKind.Cloudflare, source.captchaKind)
            assertTrue(source.isCaptchaRequired)
            assertFalse(source.isError)
        } finally {
            stateScope.cancel()
        }
    }

    @Test
    fun `rate limited state is exposed as countdown not captcha`() = runTest {
        val retryAt = 4_000_000_000_000
        val stateScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val state = createState(
            sourceResults = listOf(
                FakeMediaSourceFetchResult(
                    initialState = MediaSourceFetchState.RateLimited(retryAt = retryAt, id = 1),
                ),
            ),
            backgroundScope = stateScope,
        )
        try {
            val presentation = state.presentationFlow.first { !it.isPlaceholder }
            val source = presentation.webSources.single()

            assertFalse(source.isCaptchaRequired)
            assertTrue(source.isRateLimited)
            assertEquals(retryAt, source.rateLimitedUntilMillis)
        } finally {
            stateScope.cancel()
        }
    }

    @Test
    fun `detailed mode presentation keeps captcha action separate from failure`() {
        val request = solveRequest(WebCaptchaKind.Image)

        val presentation = MediaSourceResultPresentation(
            instanceId = "source-1",
            mediaSourceId = "source-1",
            state = MediaSourceFetchState.CaptchaRequired(request, id = 2),
            info = MediaSourceInfo(displayName = "source-1"),
            kind = MediaSourceKind.WEB,
            totalCount = 0,
            isPreferred = false,
        )

        assertTrue(presentation.isCaptchaRequired)
        assertFalse(presentation.isFailedOrAbandoned)
        assertEquals(WebCaptchaKind.Image, presentation.captchaKind)
        assertIs<SolveRequest>(presentation.captchaRequest)
    }

    @Test
    fun `detailed mode presentation exposes rate limited state`() {
        val presentation = MediaSourceResultPresentation(
            instanceId = "source-1",
            mediaSourceId = "source-1",
            state = MediaSourceFetchState.RateLimited(retryAt = 123L, id = 2),
            info = MediaSourceInfo(displayName = "source-1"),
            kind = MediaSourceKind.WEB,
            totalCount = 0,
            isPreferred = false,
        )

        assertTrue(presentation.isRateLimited)
        assertFalse(presentation.isCaptchaRequired)
        assertFalse(presentation.isFailedOrAbandoned)
        assertEquals(123L, presentation.rateLimitedUntilMillis)
    }

    private fun createState(
        sourceResults: List<MediaSourceFetchResult>,
        backgroundScope: CoroutineScope,
        mediaList: List<Media> = emptyList(),
    ): MediaSelectorState {
        return MediaSelectorState(
            mediaSelector = DefaultMediaSelector(
                mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
                mediaListNotCached = MutableStateFlow(mediaList),
                savedUserPreference = flowOf(MediaPreference.Empty),
                savedDefaultPreference = flowOf(MediaPreference.Empty),
                mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
            ),
            mediaSourceFetchResults = flowOf(sourceResults),
            mediaSourceInfoProvider = createTestMediaSourceInfoProvider(),
            preferredWebMediaSource = flowOf(null),
            backgroundScope = backgroundScope,
            webSessionManager = createTestWebSessionManager(backgroundScope),
        )
    }
}

private class FakeMediaSourceFetchResult(
    override val instanceId: String = "source-1",
    override val mediaSourceId: String = instanceId,
    override val sourceInfo: MediaSourceInfo = MediaSourceInfo(displayName = instanceId),
    override val kind: MediaSourceKind = MediaSourceKind.WEB,
    initialState: MediaSourceFetchState,
    initialResults: List<Media> = emptyList(),
) : MediaSourceFetchResult {
    override val state = MutableStateFlow(initialState)
    override val results: Flow<List<Media>> = MutableStateFlow(initialResults)

    override fun restart() {
        state.value = MediaSourceFetchState.Working
    }

    override fun enable() {
        if (state.value is MediaSourceFetchState.Disabled) {
            state.value = MediaSourceFetchState.Idle
        }
    }
}
