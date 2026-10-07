package com.wynime.app.data.network

import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.utils.ktor.asScopedHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionRemovalServiceTest {
    private fun tokens() = TokenRepository(
        MemoryDataStore(
            TokenSave(
                refreshToken = "cloud-session",
                accessTokens = TokenSave.AccessTokens("bangumi-token", "obsolete-service-token", Long.MAX_VALUE),
            ),
        ),
    )

    private fun response(status: String, subjectId: Int = 701779, url: String = "https://bgm.tv/subject/$subjectId") =
        """{"subjectId":$subjectId,"status":"$status","webUrl":"$url","expiresAt":9223372036854775807}"""

    @Test
    fun `generated client sends cloud session and returns the verified web URL`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer cloud-session", request.headers[HttpHeaders.Authorization])
            assertEquals("/api/v1/collections/701779/removal", request.url.encodedPath)
            respond(response("awaiting_web_action"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        try {
            assertEquals("https://bgm.tv/subject/701779", CollectionRemovalService(WynimeCloudClient(client.asScopedHttpClient()), tokens()).start(701779))
        } finally { client.close() }
    }

    @Test
    fun `pending web action cannot be treated as completed cancellation`() = runTest {
        val client = HttpClient(MockEngine {
            respond(response("awaiting_web_action"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        try {
            assertFailsWith<RepositoryRequestError> {
                CollectionRemovalService(WynimeCloudClient(client.asScopedHttpClient()), tokens()).confirm(701779)
            }
        } finally { client.close() }
    }

    @Test
    fun `account switch during verification rejects a confirmed response`() = runTest {
        val repository = tokens()
        val client = HttpClient(MockEngine {
            repository.clear()
            respond(response("confirmed"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        try {
            assertFailsWith<RepositoryAuthorizationException> {
                CollectionRemovalService(WynimeCloudClient(client.asScopedHttpClient()), repository).confirm(701779)
            }
        } finally { client.close() }
    }

    @Test
    fun `unrelated subject or web URL cannot authorize an operation`() = runTest {
        for (body in listOf(response("awaiting_web_action", 10), response("awaiting_web_action", url = "https://unrelated.test/"))) {
            val client = HttpClient(MockEngine {
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json() } }
            try {
                assertFailsWith<IllegalStateException> {
                    CollectionRemovalService(WynimeCloudClient(client.asScopedHttpClient()), tokens()).start(701779)
                }
            } finally { client.close() }
        }
    }

    @Test
    fun `persisted token keys read the previous session format`() {
        val saved = Json.decodeFromString<TokenSave>(
            """{"refreshToken":"cloud-session","accessTokens":{"bangumiAccessToken":"bangumi-token","aniAccessToken":"obsolete-service-token","expiresAtMillis":9223372036854775807}}""",
        )
        assertEquals("cloud-session", saved.refreshToken)
        assertEquals("obsolete-service-token", saved.accessTokens?.legacyServiceAccessToken)
    }
}
