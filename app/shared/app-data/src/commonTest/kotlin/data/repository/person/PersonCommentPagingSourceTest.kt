package com.wynime.app.data.repository.person

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.comment.CommentVoteValue
import com.wynime.app.data.models.person.PersonComment
import com.wynime.app.data.models.person.PersonCommentSource
import com.wynime.app.data.models.person.PersonCommentTarget
import com.wynime.app.data.network.WynimePersonCommentService
import com.wynime.models.CommentVoteValueDto
import com.wynime.models.EpisodeCommentAuthorDto
import com.wynime.models.EpisodeCommentReactionDto
import com.wynime.models.PersonCommentDto
import com.wynime.models.PersonCommentReplyDto
import com.wynime.models.PersonCommentSourceDto
import com.wynime.models.PersonCommentsResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PersonCommentPagingSourceTest {
    @Test
    fun `refresh keeps server order and maps fields`() = runTest {
        val source = createSource(
            pages = mapOf(
                null to PersonCommentsResponseDto(
                    total = 2,
                    items = listOf(
                        comment(
                            "ani:a", 100,
                            replies = listOf(reply("ani:a1", 110)),
                            reactions = listOf(EpisodeCommentReactionDto(value = "bgm1", count = 2, selected = true)),
                            likeCount = 3,
                            selfVote = CommentVoteValueDto.LIKE,
                        ),
                        comment("bangumi:1", 90, bangumi = true),
                    ),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
        )

        val page = assertIs<Page>(source.load(refresh(30)))
        assertEquals(listOf("ani:a", "bangumi:1"), page.data.map { it.stableId })
        assertEquals(listOf(PersonCommentSource.WYNIME, PersonCommentSource.BANGUMI), page.data.map { it.source })
        assertNull(page.nextKey)

        val ani = page.data[0]
        assertEquals("a", ani.sourceCommentId)
        assertEquals(true, ani.canReply)
        assertEquals(3, ani.likeCount)
        assertEquals(CommentVoteValue.LIKE, ani.selfVote)
        assertEquals("bgm1", ani.reactions.single().value)
        assertEquals(true, ani.reactions.single().selected)
        assertEquals("ani:a1", ani.replies.single().stableId)

        assertEquals(PersonCommentSource.WYNIME, ani.replies.single().source)
        assertEquals(false, ani.replies.single().canReply)
        assertEquals("u", ani.author?.id)

        val bangumi = page.data[1]
        assertEquals(false, bangumi.canReply)
        assertEquals(0, bangumi.likeCount)
    }

    @Test
    fun `append passes the previous cursor as after and uses the character api for characters`() = runTest {
        val requested = mutableListOf<Pair<PersonCommentTarget, String?>>()
        val source = createSource(
            target = PersonCommentTarget.Character(5),
            pages = mapOf(
                null to PersonCommentsResponseDto(
                    total = 3,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = false,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to PersonCommentsResponseDto(
                    total = 3,
                    items = listOf(comment("bangumi:1", 90, bangumi = true)),
                    bangumiUnavailable = false,
                    nextCursor = null,
                ),
            ),
            onRequest = { target, after -> requested += target to after },
        )

        val first = assertIs<Page>(source.load(refresh(30)))
        assertEquals("100:ani:a", first.nextKey)

        val second = assertIs<Page>(source.load(append(first.nextKey!!, 30)))
        assertEquals(listOf("bangumi:1"), second.data.map { it.stableId })
        assertNull(second.nextKey)
        assertEquals(
            listOf<Pair<PersonCommentTarget, String?>>(
                PersonCommentTarget.Character(5) to null,
                PersonCommentTarget.Character(5) to "100:ani:a",
            ),
            requested,
        )
    }

    @Test
    fun `reports bangumi unavailable once on the first page only`() = runTest {
        var unavailableCount = 0
        val source = createSource(
            pages = mapOf(
                null to PersonCommentsResponseDto(
                    total = 2,
                    items = listOf(comment("ani:a", 100)),
                    bangumiUnavailable = true,
                    nextCursor = "100:ani:a",
                ),
                "100:ani:a" to PersonCommentsResponseDto(
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
        assertIs<Page>(source.load(append(first.nextKey!!, 30)))
        assertEquals(1, unavailableCount)
    }

    @Test
    fun `load size is capped to the server limit`() = runTest {
        var requestedLimit: Int? = null
        val source = createSource(
            pages = mapOf(
                null to PersonCommentsResponseDto(total = 0, items = emptyList(), bangumiUnavailable = false),
            ),
            onLimit = { requestedLimit = it },
        )

        assertIs<Page>(source.load(refresh(500)))
        assertEquals(100, requestedLimit)
    }

    @Test
    fun `network failure becomes a load error`() = runTest {
        val source = createSource(pages = emptyMap())
        assertIs<PagingSource.LoadResult.Error<String, PersonComment>>(source.load(refresh(30)))
    }

    private fun createSource(
        target: PersonCommentTarget = PersonCommentTarget.Person(1),
        pages: Map<String?, PersonCommentsResponseDto>,
        onRequest: (PersonCommentTarget, String?) -> Unit = { _, _ -> },
        onLimit: (Int) -> Unit = {},
        onBangumiUnavailable: () -> Unit = {},
    ): PersonCommentPagingSource {
        val service = object : WynimePersonCommentService() {
            override suspend fun listComments(
                target: PersonCommentTarget,
                after: String?,
                limit: Int,
            ): PersonCommentsResponseDto {
                onRequest(target, after)
                onLimit(limit)
                return pages[after] ?: throw kotlinx.io.IOException("no page for $after")
            }
        }
        return PersonCommentPagingSource(target, service, onBangumiUnavailable)
    }

    private fun comment(
        id: String,
        createdAtMillis: Long,
        bangumi: Boolean = false,
        replies: List<PersonCommentReplyDto> = emptyList(),
        reactions: List<EpisodeCommentReactionDto> = emptyList(),
        likeCount: Int = 0,
        selfVote: CommentVoteValueDto? = null,
    ) = PersonCommentDto(
        id = id,
        sourceCommentId = id.substringAfter(':'),
        contentBbcode = "content $id",
        createdAtMillis = createdAtMillis,
        replyCount = replies.size,
        canReply = !bangumi,
        author = EpisodeCommentAuthorDto(id = "u", nickname = "nick", avatarUrl = null),
        briefReplies = replies,
        reactions = reactions,
        source = if (bangumi) PersonCommentSourceDto.BANGUMI else PersonCommentSourceDto.LEGACY_SERVICE,
        likeCount = likeCount,
        selfVote = selfVote,
    )

    private fun reply(id: String, createdAtMillis: Long) = PersonCommentReplyDto(
        id = id,
        sourceCommentId = id.substringAfter(':'),
        contentBbcode = "reply $id",
        createdAtMillis = createdAtMillis,
        author = EpisodeCommentAuthorDto(id = "u2", nickname = "nick2", avatarUrl = null),
        reactions = emptyList(),
    )

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

private typealias Page = PagingSource.LoadResult.Page<String, PersonComment>

