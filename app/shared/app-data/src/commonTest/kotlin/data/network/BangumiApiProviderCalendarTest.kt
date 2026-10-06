/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiApiProviderCalendarTest {
    @Test
    fun `calendar parser preserves weekday and all raw items`() = runBlocking {
        val requests = mutableListOf<String>()
        val response = """
            [
              {
                "weekday": {"id": 3},
                "items": [
                  {
                    "id": 101,
                    "type": 2,
                    "name": "Original",
                    "name_cn": "中文",
                    "images": {"large": "https://example.test/cover.jpg"}
                  },
                  {"id": 102, "type": 1, "name": "Book", "name_cn": "", "images": {}}
                ]
              }
            ]
        """.trimIndent()
        val httpClient = HttpClient(MockEngine { request ->
            requests += request.url.encodedPath
            respond(response, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        try {
            val provider = BangumiApiProvider(
                client = httpClient.asScopedHttpClient(),
                tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial)),
            )

            val days = provider.getCalendarDays()

            assertEquals(listOf("/calendar"), requests)
            assertEquals(1, days.size)
            assertEquals(3, days.single().weekdayId)
            assertEquals(listOf(101, 102), days.single().items.map { it.id })
            assertEquals("中文", days.single().items.first().nameCn)
            assertEquals("https://example.test/cover.jpg", days.single().items.first().imageLarge)
        } finally {
            httpClient.close()
        }
    }

    @Test
    fun `trending request remains a bounded first page`() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val httpClient = HttpClient(MockEngine { request ->
            requests += request.method to request.url.encodedPath
            respond(
                """{"data": [], "total": 1000}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json"),
            )
        })
        try {
            val provider = BangumiApiProvider(
                client = httpClient.asScopedHttpClient(),
                tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial)),
            )
            provider.getTrendingSubjects(limit = 50, offset = 0)

            assertEquals(listOf(HttpMethod.Get to "/p1/trending/subjects"), requests)
        } finally {
            httpClient.close()
        }
    }
}
