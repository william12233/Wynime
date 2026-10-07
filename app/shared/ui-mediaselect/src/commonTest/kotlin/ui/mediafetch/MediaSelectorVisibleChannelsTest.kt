package com.wynime.app.ui.mediafetch

import kotlin.test.Test
import kotlin.test.assertEquals
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.selector.MatchMetadata
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.TestMatchMetadata

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
}
