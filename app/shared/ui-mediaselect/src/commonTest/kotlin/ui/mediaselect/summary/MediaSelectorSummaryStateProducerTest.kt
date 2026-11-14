package com.wynime.app.ui.mediaselect.summary

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertSame
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.TestMatchMetadata
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class MediaSelectorSummaryStateProducerTest {
    @Test
    fun `selected local cache uses the shared local cache summary`() = runTest {
        val cachedMedia = CachedMedia(
            origin = TestMediaList.first(),
            cacheMediaSourceId = "local-file-system",
            download = ResourceLocation.LocalFile("/cache/episode.m3u8"),
            kind = MediaSourceKind.LocalCache,
        )
        val producer = MediaSelectorSummaryStateProducer(
            selectedMaybeExcludedMediaFlow = flowOf(
                MaybeExcludedMedia.Included(cachedMedia, TestMatchMetadata),
            ),
            mediaSourceResultsFlow = flowOf(emptyList()),
            mediaSelectorSettingsFlow = flowOf(MediaSelectorSettings.Default),
            mediaSources = flowOf(emptyList()),
        )

        assertSame(MediaSelectorSummary.LocalCache, producer.flow.first())
    }
}
