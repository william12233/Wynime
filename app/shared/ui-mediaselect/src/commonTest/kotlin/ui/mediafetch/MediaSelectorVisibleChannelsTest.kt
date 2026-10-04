/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import kotlin.test.Test
import kotlin.test.assertEquals
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.TestMatchMetadata

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
