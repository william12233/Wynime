package com.wynime.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.RepositoryRateLimitedException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.app.domain.session.AccessTokenPair
import com.wynime.models.CollectionTypeDto
import com.wynime.models.SelfRatingInfoDto
import com.wynime.models.UpdateSubjectCollectionRequestDto
import com.wynime.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BangumiSubjectServiceCollectionTest {
    @Test
    fun `not collected wish uses POST upsert`() = runBlocking {
        assertPostCollection(CollectionTypeDto.WISH)
    }

    @Test
    fun `not collected doing uses POST upsert`() = runBlocking {
        assertPostCollection(CollectionTypeDto.DOING)
    }

    @Test
    fun `existing wish to doing uses POST upsert`() = runBlocking {
        assertPostCollection(CollectionTypeDto.DOING)
    }

    @Test
    fun `existing collection to dropped uses POST upsert`() = runBlocking {
        assertPostCollection(CollectionTypeDto.DROPPED)
    }

    @Test
    fun `post preserves collection payload mapping`() = runBlocking {
        val fixture = fixture()
        try {
            fixture.service.patchSubjectCollection(
                subjectId = 456,
                payload = UpdateSubjectCollectionRequestDto(
                    collectionType = CollectionTypeDto.ON_HOLD,
                    selfRating = SelfRatingInfoDto(
                        score = 8,
                        comment = "值得重看",
                        isPrivate = true,
                        tags = listOf("收藏", "動畫"),
                    ),
                ),
            )

            val request = fixture.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v0/users/-/collections/456", request.url.encodedPath)
            val payload = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals("4", payload.getValue("type").jsonPrimitive.content)
            assertEquals("8", payload.getValue("rate").jsonPrimitive.content)
            assertEquals("值得重看", payload.getValue("comment").jsonPrimitive.content)
            assertEquals("true", payload.getValue("private").jsonPrimitive.content)
            assertEquals(listOf("收藏", "動畫"), payload.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `collection errors keep authorization rate request and network categories`() = runBlocking {
        assertFailsWith<RepositoryAuthorizationException> {
            requestWithStatus(HttpStatusCode.Unauthorized)
        }
        assertFailsWith<RepositoryAuthorizationException> {
            requestWithStatus(HttpStatusCode.Forbidden)
        }
        assertFailsWith<RepositoryRateLimitedException> {
            requestWithStatus(HttpStatusCode.TooManyRequests)
        }
        assertFailsWith<RepositoryRequestError> {
            requestWithStatus(HttpStatusCode.BadRequest)
        }

        val tokenRepository = loggedInTokenRepository()
        val httpClient = HttpClient(MockEngine { throw IOException("offline") }) { expectSuccess = true }
        try {
            val service = BangumiSubjectService(
                BangumiApiProvider(httpClient.asScopedHttpClient(), tokenRepository),
            )
            assertFailsWith<RepositoryNetworkException> {
                service.patchSubjectCollection(
                    123,
                    UpdateSubjectCollectionRequestDto(collectionType = CollectionTypeDto.WISH),
                )
            }
        } finally {
            httpClient.close()
        }
    }

    private suspend fun assertPostCollection(type: CollectionTypeDto) {
        val fixture = fixture()
        try {
            fixture.service.patchSubjectCollection(
                subjectId = 123,
                payload = UpdateSubjectCollectionRequestDto(collectionType = type),
            )
            val request = fixture.requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v0/users/-/collections/123", request.url.encodedPath)
            assertTrue(fixture.requests.none { it.method == HttpMethod.Patch })
        } finally {
            fixture.close()
        }
    }

    private suspend fun requestWithStatus(status: HttpStatusCode) {
        val fixture = fixture(status = status)
        try {
            fixture.service.patchSubjectCollection(
                123,
                UpdateSubjectCollectionRequestDto(collectionType = CollectionTypeDto.WISH),
            )
        } finally {
            fixture.close()
        }
    }

    private suspend fun fixture(status: HttpStatusCode = HttpStatusCode.OK): Fixture {
        val requests = mutableListOf<HttpRequestData>()
        val httpClient = HttpClient(MockEngine { request ->
            requests += request
            respond("", status, headersOf("Content-Type", "application/json"))
        }) {
            install(ContentNegotiation) { json() }
            expectSuccess = true
        }
        return Fixture(
            service = BangumiSubjectService(
                bangumiApi = BangumiApiProvider(
                    client = httpClient.asScopedHttpClient(),
                    tokenRepository = loggedInTokenRepository(),
                ),
            ),
            requests = requests,
            httpClient = httpClient,
        )
    }

    private suspend fun loggedInTokenRepository(): TokenRepository {
        val repository = TokenRepository(MemoryDataStore(TokenSave.Initial))
        repository.setSession(
            AccessTokenSession(
                AccessTokenPair(
                    legacyServiceAccessToken = "ani-test-token",
                    expiresAtMillis = Long.MAX_VALUE,
                    bangumiAccessToken = "bgm-test-token",
                ),
            ),
        )
        return repository
    }

    private class Fixture(
        val service: BangumiSubjectService,
        val requests: MutableList<HttpRequestData>,
        private val httpClient: HttpClient,
    ) {
        fun close() {
            httpClient.close()
        }
    }
}
