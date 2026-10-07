/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.network.BangumiApiProvider
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.app.domain.session.AccessTokenPair
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BangumiTrackingSyncApiTest {
    @Test
    fun `authenticated sync API uses me paged anime collections and type only POST`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            when (request.url.encodedPath) {
                "/v0/me" -> respond(
                    """
                    {
                      "id": 1060673,
                      "username": "1060673",
                      "nickname": "William",
                      "user_group": "10",
                      "avatar": {"large":"","medium":"","small":""},
                      "sign": ""
                    }
                    """.trimIndent(),
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )

                "/v0/users/1060673/collections" -> respond(
                    """
                    {
                      "total": 1,
                      "limit": 100,
                      "offset": 0,
                      "data": [{
                        "subject_id": 123,
                        "subject_type": 2,
                        "rate": 8,
                        "type": 3,
                        "tags": ["keep"],
                        "ep_status": 2,
                        "vol_status": 0,
                        "updated_at": "2026-01-01T00:00:00Z",
                        "private": false,
                        "comment": "keep",
                        "subject": null
                      }]
                    }
                    """.trimIndent(),
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )

                "/v0/users/-/collections/123" -> {
                    check(request.method == HttpMethod.Post) {
                        "Unexpected method for collection mutation: ${request.method}"
                    }
                    respond("", HttpStatusCode.NoContent)
                }

                "/v0/users/1060673/collections/123" -> respond(
                    """
                    {
                      "subject_id": 123,
                      "subject_type": 2,
                      "rate": 8,
                      "type": 2,
                      "tags": [],
                      "ep_status": 2,
                      "vol_status": 0,
                      "updated_at": "2026-01-01T00:00:01Z",
                      "private": false,
                      "comment": "",
                      "subject": null
                    }
                    """.trimIndent(),
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )

                else -> error("Unexpected Bangumi request: ${request.method} ${request.url}")
            }
        }) {
            install(ContentNegotiation) {
                json(Json { explicitNulls = false })
            }
            expectSuccess = true
        }
        try {
            val provider = BangumiApiProvider(
                client = client.asScopedHttpClient(),
                tokenRepository = loggedInTokenRepository(),
            )
            val api = BangumiTrackingSyncApiImpl(provider)

            assertEquals(BangumiTrackingAccount(1060673, "1060673"), api.currentUser())
            val page = api.animeCollections("1060673", limit = 100, offset = 0)
            assertEquals(1, page.total)
            assertEquals(UnifiedCollectionType.DOING, page.collections.single().type)
            api.upsertCollectionType(123, UnifiedCollectionType.DONE)
            assertEquals(UnifiedCollectionType.DONE, api.collection("1060673", 123)?.type)

            val collectionRequest = requests.first { it.url.encodedPath == "/v0/users/1060673/collections" }
            assertEquals(HttpMethod.Get, collectionRequest.method)
            assertEquals("2", collectionRequest.url.parameters["subject_type"])
            assertEquals("100", collectionRequest.url.parameters["limit"])
            assertEquals("0", collectionRequest.url.parameters["offset"])

            val mutation = requests.first { it.url.encodedPath == "/v0/users/-/collections/123" }
            assertEquals(HttpMethod.Post, mutation.method)
            val body = mutation.body.toByteArray().decodeToString()
            val payload = Json.parseToJsonElement(body).jsonObject
            assertEquals(setOf("type"), payload.keys)
            assertEquals("2", payload.getValue("type").jsonPrimitive.content)
            assertFalse(requests.any { it.method == HttpMethod.Delete })
            assertTrue(requests.any { it.url.encodedPath == "/v0/me" })
        } finally {
            client.close()
        }
    }

    private suspend fun loggedInTokenRepository(): TokenRepository {
        val repository = TokenRepository(MemoryDataStore(TokenSave.Initial))
        repository.setSession(
            AccessTokenSession(
                AccessTokenPair(
                    aniAccessToken = "ani-test-token",
                    expiresAtMillis = Long.MAX_VALUE,
                    bangumiAccessToken = "bgm-test-token",
                ),
            ),
        )
        return repository
    }
}
