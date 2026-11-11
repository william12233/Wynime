package com.wynime.app.ui.mediafetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.selector.MatchMetadata
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.TestMatchMetadata
import com.wynime.app.domain.media.fetch.MediaSourceFetchState
import com.wynime.app.ui.mediaselect.selector.WebSourceChannel
import com.wynime.app.domain.mediasource.web.PageExpectation
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.app.domain.mediasource.web.WebCaptchaKind
import com.wynime.source.plugin.api.SourceDiagnostics
import com.wynime.source.plugin.api.SourceResultStatus

class MediaSelectorVisibleChannelsTest {
    @Test
    fun `simple mode excludes other season candidates from source channels`() {
        val candidates = listOf(
            MaybeExcludedMedia.Included(TestMediaList[0], TestMatchMetadata),
            MaybeExcludedMedia.Excluded(
                original = TestMediaList[2],
                exclusionReason = MediaExclusionReason.FromSeriesSeason,
            ),
        )

        assertEquals(
            listOf(TestMediaList[0]),
            visibleSourceMedia(candidates, TestMediaList[0].mediaSourceId),
        )
    }

    @Test
    fun `simple mode keeps every current season channel`() {
        val secondChannel = TestMediaList[0].copy(
            mediaId = "${TestMediaList[0].mediaId}-second",
            properties = TestMediaList[0].properties.copy(alliance = "第二線路"),
        )
        val candidates = listOf(
            MaybeExcludedMedia.Included(TestMediaList[0], TestMatchMetadata),
            MaybeExcludedMedia.Included(
                secondChannel,
                TestMatchMetadata.copy(
                    subjectMatchKind = MatchMetadata.SubjectMatchKind.EXACT,
                ),
            ),
        )

        assertEquals(
            listOf(TestMediaList[0], secondChannel),
            visibleSourceMedia(candidates, TestMediaList[0].mediaSourceId),
        )
    }

    @Test
    fun `source result count uses distinct playback lines`() {
        val sameLine = TestMediaList[0].copy(mediaId = "${TestMediaList[0].mediaId}-episode-2")
        val secondLine = TestMediaList[0].copy(
            mediaId = "${TestMediaList[0].mediaId}-second-line",
            properties = TestMediaList[0].properties.copy(alliance = "第二線路"),
        )

        assertEquals(2, listOf(TestMediaList[0], sameLine, secondLine).countPlaybackLines())
    }

    @Test
    fun `simple mode keeps verification and loading states without channels`() {
        val channels = emptyList<WebSourceChannel>()

        assertTrue(
            shouldShowSimpleWebSource(
                MediaSourceFetchState.CaptchaRequired(
                    request = SolveRequest(
                        mediaSourceId = "source",
                        pageUrl = "https://example.com/search",
                        kind = WebCaptchaKind.Cloudflare,
                        expectation = PageExpectation.AnyContent,
                    ),
                    id = 1,
                ),
                channels,
            ),
        )
        assertTrue(shouldShowSimpleWebSource(MediaSourceFetchState.Working, channels))
        assertTrue(shouldShowSimpleWebSource(MediaSourceFetchState.Idle, channels))
    }

    @Test
    fun `simple mode hides completed states without channels`() {
        val channels = emptyList<WebSourceChannel>()

        assertFalse(shouldShowSimpleWebSource(MediaSourceFetchState.Succeed(1), channels))
        assertFalse(
            shouldShowSimpleWebSource(
                MediaSourceFetchState.NoMatch(
                    SourceDiagnostics(
                        traceId = "trace",
                        provider = "source",
                        entryPoint = "search",
                        responseCategory = SourceResultStatus.SUBJECT_NO_MATCH,
                    ),
                    1,
                ),
                channels,
            ),
        )
        assertFalse(shouldShowSimpleWebSource(MediaSourceFetchState.Failed(IllegalStateException(), 1), channels))
        assertFalse(shouldShowSimpleWebSource(MediaSourceFetchState.Abandoned(IllegalStateException(), 1), channels))
        assertFalse(shouldShowSimpleWebSource(MediaSourceFetchState.RateLimited(123L, 1), channels))
        assertTrue(
            shouldShowSimpleWebSource(
                MediaSourceFetchState.Failed(IllegalStateException(), 1),
                listOf(WebSourceChannel("可用線路")),
            ),
        )
    }
}
