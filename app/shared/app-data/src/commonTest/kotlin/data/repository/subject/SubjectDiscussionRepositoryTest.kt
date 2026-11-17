package com.wynime.app.data.repository.subject

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.subject.SubjectDiscussion
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SubjectDiscussionRepositoryTest {
    @Test
    fun `reads opening posts in board order and advances by the number of topics returned`() = runTest {
        val offsets = mutableListOf<String>()
        withRepository({ request ->
            when (request.url.encodedPath) {
                "/p1/subjects/425222/topics" -> {
                    assertEquals(HttpMethod.Get, request.method)
                    assertNull(request.headers[HttpHeaders.Authorization])
                    assertEquals("10", request.url.parameters["limit"])
                    val offset = checkNotNull(request.url.parameters["offset"])
                    offsets += offset
                    if (offset == "0") {
                        respond(
                            """{"data": [${topic(29838, "Thread one")}, ${topic(29587, "Thread two")}], "total": 3}""",
                        )
                    } else {
                        respond("""{"data": [${topic(29366, "Thread three")}], "total": 3}""")
                    }
                }

                "/p1/subjects/-/topics/29838" -> respond(
                    """{"replies": [
                        {"creatorID": 123, "createdAt": 1716093004, "state": 0, "content": "[b]Opening post[/b]"},
                        {"creatorID": 456, "createdAt": 1716093050, "state": 0, "content": "A reply"}
                    ]}""",
                )

                "/p1/subjects/-/topics/29587" -> respond(
                    """{"content": "Top-level opening post", "replies": [
                        {"creatorID": 123, "createdAt": 1716093004, "state": 0, "content": "Another reply"}
                    ]}""",
                )

                "/p1/subjects/-/topics/29366" -> respond(
                    """{"replies": [
                        {"creatorID": 123, "createdAt": 1716093005, "state": 0, "content": "Later post by the original author"}
                    ]}""",
                )

                else -> error("Unexpected request ${request.url}")
            }
        }) { repository ->
            val source = repository.pagingSource(425222)
            val first = assertIs<LoadResult.Page<Int, SubjectDiscussion>>(source.load(firstPage()))
            assertEquals(listOf(29838, 29587), first.data.map { it.id })
            assertEquals(listOf("[b]Opening post[/b]", "Top-level opening post"), first.data.map { it.content })
            assertEquals("Author", first.data.first().author.nickname)
            assertEquals(1716093004000, first.data.first().createdAtMillis)
            assertEquals("https://bgm.tv/subject/topic/29838", first.data.first().originalUrl)
            assertEquals(2, first.nextKey)

            val last = assertIs<LoadResult.Page<Int, SubjectDiscussion>>(
                source.load(LoadParams.Append(checkNotNull(first.nextKey), 10, false)),
            )
            assertEquals(29366, last.data.single().id)
            assertNull(last.data.single().content)
            assertNull(last.nextKey)
            assertEquals(listOf("0", "2"), offsets)
        }
    }

    @Test
    fun `retries a transient error and retains a topic with an inaccessible opening post`() = runTest {
        var detailAttempts = 0
        withRepository({ request ->
            when (request.url.encodedPath) {
                "/p1/subjects/425222/topics" -> respond("""{"data": [${topic(29838, "Thread one")}], "total": 1}""")
                "/p1/subjects/-/topics/29838" -> {
                    detailAttempts++
                    respond("{}", if (detailAttempts == 1) HttpStatusCode.BadGateway else HttpStatusCode.NotFound)
                }

                else -> error("Unexpected request ${request.url}")
            }
        }) { repository ->
            val source = repository.pagingSource(425222)
            assertIs<LoadResult.Error<Int, SubjectDiscussion>>(source.load(firstPage()))
            val page = assertIs<LoadResult.Page<Int, SubjectDiscussion>>(source.load(firstPage()))
            assertEquals("Thread one", page.data.single().title)
            assertNull(page.data.single().content)
            assertNull(page.nextKey)
            assertEquals(2, detailAttempts)
        }
    }

    @Test
    fun `an empty board terminates without requesting any topic details`() = runTest {
        withRepository({ request ->
            check(request.url.encodedPath == "/p1/subjects/425222/topics")
            respond("""{"data": [], "total": 0}""")
        }) { repository ->
            val page = assertIs<LoadResult.Page<Int, SubjectDiscussion>>(
                repository.pagingSource(425222).load(firstPage()),
            )
            assertEquals(emptyList(), page.data)
            assertNull(page.nextKey)
        }
    }

    private fun firstPage() = LoadParams.Refresh<Int>(key = null, loadSize = 10, placeholdersEnabled = false)

    private fun topic(id: Int, title: String) = """
        {"id": $id, "title": "$title", "creatorID": 123, "createdAt": 1716093004, "replyCount": 47,
         "creator": {"id": 123, "nickname": "Author", "username": "author"}}
    """.trimIndent()

    private suspend fun withRepository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        block: suspend (SubjectDiscussionRepository) -> Unit,
    ) {
        val httpClient = HttpClient(MockEngine(handler))
        try {
            val provider = BangumiApiProvider(httpClient.asScopedHttpClient(), TokenRepository(MemoryDataStore(TokenSave.Initial)))
            block(SubjectDiscussionRepository(provider))
        } finally {
            httpClient.close()
        }
    }
}
