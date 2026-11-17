package com.wynime.app.data.repository.subject

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.network.BangumiRelatedPeopleService
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.persistent.database.createTestWynimeDatabase
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectAnimeRelationsTest {
    @Test
    fun `cards and the series graph keep anime relationships only`() = runTest {
        val responseHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val http = HttpClient(MockEngine { request ->
            val body = when (val path = request.url.encodedPath) {
                "/v0/subjects/1/subjects" -> """[
                    {"id": 3, "type": 1, "relation": "主线故事", "name": "Book", "name_cn": ""},
                    {"id": 4, "type": 4, "relation": "续集", "name": "Game", "name_cn": ""},
                    {"id": 5, "type": 3, "relation": "衍生", "name": "Soundtrack", "name_cn": ""},
                    {"id": 2, "type": 2, "relation": "续集", "name": "Anime sequel", "name_cn": ""},
                    {"id": 6, "type": 2, "relation": "番外篇", "name": "Anime OVA", "name_cn": ""}
                ]"""

                "/v0/subjects/2/subjects" -> "[]"
                "/v0/subjects/1", "/v0/subjects/2" -> subject(path.substringAfterLast('/').toInt())
                else -> error("Unexpected request $path")
            }
            respond(body, headers = responseHeaders)
        }) { install(ContentNegotiation) { json() } }
        val database = createTestWynimeDatabase()
        try {
            val api = BangumiApiProvider(http.asScopedHttpClient(), TokenRepository(MemoryDataStore(TokenSave.Initial)))
            val cards = BangumiRelatedPeopleService(api).relatedSubjectsFlow(1).first()
            assertEquals(setOf(2, 6), cards.map { it.subjectId }.toSet())

            val graph = SubjectRelationGraphRepository(api, database.subjectCollection()).subjectRelationGraphFlow(1).first()
            assertEquals(listOf(1, 2), graph.mainline.map { it.subject.subjectId })
            assertEquals(6, graph.mainline.first().branches.single().subject.subjectId)
        } finally {
            database.close()
            http.close()
        }
    }

    private fun subject(id: Int) = """{
        "id": $id, "type": 2, "name": "Anime $id", "name_cn": "", "summary": "", "nsfw": false,
        "locked": false, "platform": "TV", "volumes": 0, "eps": 12, "total_episodes": 12, "tags": [],
        "images": {"large": "", "common": "", "medium": "", "small": "", "grid": ""},
        "rating": {"rank": 0, "total": 0, "count": {}, "score": 0},
        "collection": {"wish": 0, "collect": 0, "doing": 0, "on_hold": 0, "dropped": 0}
    }"""
}
