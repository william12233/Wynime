package com.wynime.datasources.bangumi

import com.wynime.datasources.bangumi.apis.DefaultApi
import com.wynime.datasources.bangumi.next.apis.EpisodeBangumiNextApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BangumiBearerAuthenticationTest {
    @Test
    fun `getMyself sends the bearer token through HTTPBearer`() = runTest {
        val request = captureRequest { httpClient ->
            val api = DefaultApi(TEST_BASE_URL, httpClient)
            api.setBearerToken(TEST_TOKEN)
            val response = api.getMyself()

            assertEquals(200, response.status)
        }

        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v0/me", request.url.encodedPath)
        assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `the same bearer token is applied to an OptionalHTTPBearer endpoint`() = runTest {
        val request = captureRequest { httpClient ->
            val api = DefaultApi(TEST_BASE_URL, httpClient)
            api.setBearerToken(TEST_TOKEN)
            api.getSubjectById(42)
        }

        assertEquals("/v0/subjects/42", request.url.encodedPath)
        assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `bearer authentication does not duplicate its prefix`() = runTest {
        val request = captureRequest { httpClient ->
            val api = DefaultApi(TEST_BASE_URL, httpClient)
            api.setBearerToken(TEST_TOKEN)
            api.getMyself()
        }

        val authorization = request.headers[HttpHeaders.Authorization]
        assertEquals("Bearer $TEST_TOKEN", authorization)
        assertFalse(authorization.orEmpty().contains("Bearer Bearer"))
    }

    @Test
    fun `anonymous endpoints do not receive a bearer header`() = runTest {
        val request = captureRequest { httpClient ->
            DefaultApi(TEST_BASE_URL, httpClient).getSubjectRevisionByRevisionId(42)
        }

        assertEquals("/v0/revisions/subjects/42", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `Bangumi Next generated client sends the bearer token`() = runTest {
        val request = captureRequest { httpClient ->
            val api = EpisodeBangumiNextApi(TEST_BASE_URL, httpClient)
            api.setBearerToken(TEST_TOKEN)
            api.getEpisode(42)
        }

        assertEquals("/p1/episodes/42", request.url.encodedPath)
        assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `Bangumi Next anonymous endpoints do not receive a bearer header`() = runTest {
        val request = captureRequest { httpClient ->
            val api = EpisodeBangumiNextApi(TEST_BASE_URL, httpClient)
            api.setBearerToken(TEST_TOKEN)
            api.getEpisodeComments(42)
        }

        assertEquals("/p1/episodes/42/comments", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    private suspend fun captureRequest(call: suspend (HttpClient) -> Unit): HttpRequestData {
        var captured: HttpRequestData? = null
        val client = HttpClient(
            MockEngine { request ->
                captured = request
                respond("{}")
            },
        )
        try {
            call(client)
        } finally {
            client.close()
        }
        return captured ?: error("The generated client did not issue a request")
    }

    private companion object {
        const val TEST_BASE_URL = "https://bangumi.example.test"
        const val TEST_TOKEN = "test-access-token"
    }
}
