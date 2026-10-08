package com.wynime.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChineseSearchKeywordTest {
    @Test
    fun `traditional Chinese title is converted for Bangumi search`() {
        assertEquals(
            "我独自升级",
            WynimeSubjectSearchService.sanitizeKeyword("我獨自升級"),
        )
    }

    @Test
    fun `simplified Chinese title is unchanged`() {
        assertEquals(
            "我独自升级",
            WynimeSubjectSearchService.sanitizeKeyword("我独自升级"),
        )
    }

    @Test
    fun `punctuation sanitization happens before conversion`() {
        assertEquals(
            "我独自升级 第二季",
            WynimeSubjectSearchService.sanitizeKeyword("我獨自升級：第二季"),
        )
    }

    @Test
    fun `Bangumi request falls back from the original keyword to the converted keyword`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val httpClient = HttpClient(MockEngine { request ->
            requests += request
            respond(
                content = "{\"total\":0,\"data\":[]}",
                headers = headersOf("Content-Type", "application/json"),
            )
        }) {
            install(ContentNegotiation) { json() }
            expectSuccess = true
        }

        try {
            WynimeSubjectSearchService(
                bangumiApi = BangumiApiProvider(
                    client = httpClient.asScopedHttpClient(),
                    tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial)),
                ),
            ).searchSubjects("我獨自升級", limit = 20)

            val bodies = requests.map { it.body.toByteArray().decodeToString() }
            assertTrue(bodies.first().contains("\"keyword\":\"我獨自升級\""))
            assertTrue(bodies.last().contains("\"keyword\":\"我独自升级\""))
            assertEquals(2, requests.size)
        } finally {
            httpClient.close()
        }
    }
}
