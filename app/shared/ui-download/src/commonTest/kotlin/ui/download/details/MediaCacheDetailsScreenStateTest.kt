package com.wynime.app.ui.download.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.TestMediaSourceInfo
import com.wynime.app.domain.media.cache.TestMediaCache
import com.wynime.datasources.api.CachedMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.MediaCacheMetadata

class MediaCacheDetailsScreenStateTest {
    @Test
    fun `falls back to origin details when cached media is unavailable`() = runTest {
        val mediaOrigin = TestMediaList.first()
        val cache = object : TestMediaCache(
            media = CachedMedia(mediaOrigin, "local-cache", mediaOrigin.download),
            metadata = MediaCacheMetadata(
                subjectId = "1",
                episodeId = "1",
                subjectNameCN = "subject",
                subjectNames = listOf("subject"),
                episodeSort = EpisodeSort(1),
                episodeEp = EpisodeSort(1),
                episodeName = "episode",
            ),
        ) {
            override val origin = mediaOrigin

            override suspend fun getCachedMedia() = error("Download not completed")
        }

        val state = createMediaCacheDetailsScreenState(cache, TestMediaSourceInfo)

        val details = assertNotNull(state.details)
        assertEquals(mediaOrigin.originalTitle, details.originalTitle)
        assertEquals(mediaOrigin.originalUrl, details.originalUrl)
        assertEquals(TestMediaSourceInfo, details.sourceInfo)
        assertNull(details.localCacheFilePath)
        assertNull(details.contentDownloadUri)
    }

    @Test
    fun `returns placeholder when cache is missing`() = runTest {
        val state = createMediaCacheDetailsScreenState(mediaCache = null, sourceInfo = TestMediaSourceInfo)
        assertNull(state.details)
    }
}
