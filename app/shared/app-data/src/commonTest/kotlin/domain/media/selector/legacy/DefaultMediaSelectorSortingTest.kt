@file:Suppress("DEPRECATION")

package com.wynime.app.domain.media.selector.legacy

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.source.MediaSourceKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@Deprecated(MediaSelectorDeprecationMessage)
class DefaultMediaSelectorSortingTest : AbstractDefaultMediaSelectorTest() {

    @Test
    fun `sort by subjectName similarity`() = runTest {
        preferAnyMedia()
        val expectedSort = listOf(
            media(subjectName = "孤独摇滚", kind = MediaSourceKind.WEB),
            media(subjectName = "Bocchi The Rock!", kind = MediaSourceKind.WEB),
            media(subjectName = "孤独摇滚!", kind = MediaSourceKind.WEB),
            media(subjectName = "孤独摇滚 第二季", kind = MediaSourceKind.WEB),
            media(subjectName = "孤单摇滚", kind = MediaSourceKind.WEB),
            media(subjectName = "孤单摇滚 第二季", kind = MediaSourceKind.WEB),
        )
        addMedia(*expectedSort.toTypedArray<DefaultMedia>().apply { shuffle(Random(10000)) })
        assertEquals(
            expectedSort.indices.toList().take(4),
            selector.filteredCandidatesMedia.first().map { expectedSort.indexOf(it) },
        )
    }

    @Test
    fun `sort by subjectName filters out unrelated WEB`() = runTest {
        preferAnyMedia()
        val expectedSort = listOf(
            media(subjectName = "unrelated item unrelated item", kind = MediaSourceKind.WEB),
        )
        addMedia(*expectedSort.toTypedArray())
        assertEquals(
            listOf(MediaExclusionReason.SubjectNameMismatch),
            selector.preferredCandidates.first().map { it.exclusionReason },
        )
    }

    @Test
    fun `sort by subjectName does not filter out Cache`() = runTest {
        preferAnyMedia()
        val expectedSort = listOf(
            media(subjectName = "unrelated item", kind = MediaSourceKind.LocalCache),
        )
        addMedia(*expectedSort.toTypedArray())
        assertEquals(
            listOf(null),
            selector.filteredCandidates.first().map { it.exclusionReason },
        )
    }

    @Test
    fun `preferKind is null - keep original order`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(preferKind = null)
        val expectedSort = listOf(
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.WEB),
            media(kind = MediaSourceKind.WEB),
        )
        addMedia(*expectedSort.toTypedArray())
        assertEquals(
            expectedSort.indices.toList(),
            selector.filteredCandidatesMedia.first().map { expectedSort.indexOf(it) },
        )
    }

    inner class PreferKindMedias {
        val cache1 = media(mediaId = "cache1", kind = MediaSourceKind.LocalCache)
        val cache2 = media(mediaId = "cache2", kind = MediaSourceKind.LocalCache)
        val web1 = media(mediaId = "web1", kind = MediaSourceKind.WEB)
        val web2 = media(mediaId = "web2", kind = MediaSourceKind.WEB)
    }

    @Test
    fun `preferKind is null - cache always on top`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(preferKind = null)
        PreferKindMedias().run {
            addMedia(web1, web2, cache1)
            assertEquals(
                cache1,
                selector.filteredCandidatesMedia.first()[0],
            )
        }
    }

    @Test
    fun `preferKind is WEB - WEB first`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(preferKind = MediaSourceKind.WEB)
        PreferKindMedias().run {
            addMedia(web1, web2)
            assertEquals(
                listOf(web1, web2),
                selector.filteredCandidatesMedia.first(),
            )
        }
    }

    @Test
    fun `preferKind is WEB - cache on top`() = runTest {
        preferAnyMedia()
        mediaSelectorSettings.value = MediaSelectorSettings.Companion.Default.copy(preferKind = MediaSourceKind.WEB)
        PreferKindMedias().run {
            addMedia(web1, web2, cache1)
            assertEquals(
                listOf(cache1, web1, web2),
                selector.filteredCandidatesMedia.first(),
            )
        }
    }

    private fun preferAnyMedia() {
        mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            subjectInfo = SubjectInfo.Companion.Empty.copy(nameCn = "孤独摇滚", name = "Bocchi The Rock!"),
            episodeInfo = EpisodeInfo.Companion.Empty.copy(sort = EpisodeSort(1)),
        )
        savedDefaultPreference.value = MediaPreference.Companion.Any
        savedUserPreference.value = MediaPreference.Companion.Any
    }
}
