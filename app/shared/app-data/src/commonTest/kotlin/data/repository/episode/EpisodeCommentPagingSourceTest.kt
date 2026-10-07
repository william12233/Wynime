package com.wynime.app.data.repository.episode

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.episode.EpisodeComment
import com.wynime.app.data.models.episode.EpisodeCommentSource
import com.wynime.app.data.network.WynimeEpisodeCommentService
import com.wynime.models.EpisodeCommentDto
import com.wynime.models.EpisodeCommentSourceDto
import com.wynime.models.EpisodeCommentsResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EpisodeCommentPagingSourceTest {
    @Test
    fun `refresh keeps server order and carries source through`() = runTest {
        val source = createSource(
            pages = mapOf(
                null to EpisodeCommentsResponseDto(
                    total = 2,
                    items = listOf(comment("ani:a", 100), comment("bangumi:1", 90, bangumi = true)),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
        )

        val page = assertIs<Page>(source.load(refresh(30)))
        assertEquals(listOf("ani:a", "bangumi:1"), page.data.map { it.stableId })
        assertEquals(
            listOf(EpisodeCommentSource.WYNIME, EpisodeCommentSource.BANGUMI),
            page.data.map { it.source },
        )
        assertNull(page.nextKey)
    }

    @Test
    fun `append passes the previous cursor as after`() = runTest {
        val requested = mutableListOf<String?>()
        val source = createSource(
            pages = mapOf(
                null to EpisodeCommentsResponseDto(
                    total = 3,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = false,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to EpisodeCommentsResponseDto(
                    total = 3,
                    items = listOf(comment("bangumi:1", 90, bangumi = true)),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
            onRequest = { requested += it },
        )

        val first = assertIs<Page>(source.load(refresh(30)))
        assertEquals("100:ani:a", first.nextKey)

        val second = assertIs<Page>(source.load(append(first.nextKey!!, 30)))
        assertEquals(listOf("bangumi:1"), second.data.map { it.stableId })
        assertNull(second.nextKey)
        assertEquals(listOf(null, "100:ani:a"), requested)
    }

    @Test
    fun `reports bangumi unavailable once on the first page only`() = runTest {
        var unavailableCount = 0
        val source = createSource(
            pages = mapOf(
                null to EpisodeCommentsResponseDto(
                    total = 2,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = true,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to EpisodeCommentsResponseDto(
                    total = 2,
                    items = listOf(comment("ani:b", 90)),
                    bangumiUnavailable = true,
                    nextCursor = null,
                ),
            ),
            onBangumiUnavailable = { unavailableCount++ },
        )

        val first = assertIs<Page>(source.load(refresh(30)))
        assertEquals(1, unavailableCount)

        assertEquals(listOf("ani:a"), first.data.map { it.stableId })

        source.load(append(first.nextKey!!, 30))

        assertEquals(1, unavailableCount)
    }

    @Test
    fun `clamps load size to the server limit`() = runTest {
        val limits = mutableListOf<Int>()
        val source = createSource(
            pages = mapOf(
                null to EpisodeCommentsResponseDto(
                    total = 0,
                    items = emptyList(),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
            onLimit = { limits += it },
        )

        source.load(refresh(300))
        assertEquals(listOf(100), limits)
    }

    private fun createSource(
        pages: Map<String?, EpisodeCommentsResponseDto>,
        onRequest: (String?) -> Unit = {},
        onLimit: (Int) -> Unit = {},
        onBangumiUnavailable: () -> Unit = {},
    ): EpisodeCommentPagingSource {
        return EpisodeCommentPagingSource(
            episodeId = 99L,
            wynimeCommentService = object : WynimeEpisodeCommentService() {
                override suspend fun listEpisodeComments(
                    episodeId: Long,
                    after: String?,
                    limit: Int,
                ): EpisodeCommentsResponseDto {
                    onRequest(after)
                    onLimit(limit)
                    return pages[after]
                        ?: EpisodeCommentsResponseDto(
                            total = 0,
                            items = emptyList(),
                            bangumiUnavailable = false,
                            nextCursor = null,
                        )
                }
            },
            onBangumiUnavailable = onBangumiUnavailable,
        )
    }

    private fun comment(id: String, createdAt: Long, bangumi: Boolean = false): EpisodeCommentDto {
        return EpisodeCommentDto(
            id = id,
            sourceCommentId = id.substringAfter(':'),
            episodeId = 99L,
            contentBbcode = id,
            createdAtMillis = createdAt,
            replyCount = 0,
            briefReplies = emptyList(),
            reactions = emptyList(),
            canReply = !bangumi,
            source = if (bangumi) EpisodeCommentSourceDto.BANGUMI else EpisodeCommentSourceDto.LEGACY_SERVICE,
            likeCount = 0,
        )
    }

    private fun refresh(loadSize: Int) = PagingSource.LoadParams.Refresh<String>(
        key = null,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )

    private fun append(key: String, loadSize: Int) = PagingSource.LoadParams.Append(
        key = key,
        loadSize = loadSize,
        placeholdersEnabled = false,
    )
}

private typealias Page = PagingSource.LoadResult.Page<String, EpisodeComment>

