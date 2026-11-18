package com.wynime.app.data.repository.subject

import androidx.datastore.preferences.core.emptyPreferences
import com.wynime.app.data.network.WynimeCloudClient
import com.wynime.app.data.network.WynimePlaybackSyncRequest
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.persistent.database.createTestWynimeDatabase
import com.wynime.app.data.persistent.database.dao.createMemoryPlaybackHistoryDao
import com.wynime.app.data.repository.player.EpisodeHistories
import com.wynime.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import com.wynime.app.data.repository.player.PlaybackHistorySyncer
import com.wynime.app.data.repository.user.PreferencesRepositoryImpl
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.app.domain.session.SessionEvent
import com.wynime.app.domain.session.SessionState
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.utils.ktor.asScopedHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WynimeCloudEventSyncTest {
    private val tokens = TokenRepository(MemoryDataStore(TokenSave(refreshToken = "cloud-session")))
    private val session = object : SessionStateProvider {
        override val stateFlow = MutableStateFlow<SessionState>(SessionState.Valid(bangumiConnected = true))
        override val eventFlow = MutableSharedFlow<SessionEvent>()
    }

    @Test
    fun `computer uploads a change and an idle phone pulls it upon activation`() = runTest {
        var requests = 0
        var revision = 0
        var positionMillis = 0L
        val client = mockCloud { request ->
            assertEquals("/api/v1/playback/sync", request.url.encodedPath)
            requests += 1
            val body = Json.decodeFromString<WynimePlaybackSyncRequest>((request.body as TextContent).text)
            body.changes.singleOrNull()?.let {
                positionMillis = it.positionMs
                revision += 1
            }
            val changes = if (body.cursor < revision) {
                """[{"subjectId":10,"episodeId":2,"positionMs":$positionMillis,"durationMs":100000,
                    "completed":false,"lastPlayedAt":100,"revision":$revision}]"""
            } else "[]"
            respondJson("""{"cursor":$revision,"serverChanges":$changes}""")
        }

        try {
            val cloudClient = WynimeCloudClient(client.asScopedHttpClient())
            val (computer, computerHistory) = playbackDevice(cloudClient)
            val (phone, phoneHistory) = playbackDevice(cloudClient)
            computer.setForeground(true)
            runCurrent()
            assertEquals(1, requests)

            computerHistory.saveOrUpdate(episodeId = 2, positionMillis = 20_000, subjectId = 10, durationMillis = 100_000)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, requests)
            assertEquals(emptyList(), computerHistory.pendingOpsFlow.first())
            assertEquals(null, phoneHistory.getPositionMillisByEpisodeId(2))

            phone.setForeground(true)
            runCurrent()
            assertEquals(3, requests)
            assertEquals(20_000L, phoneHistory.getPositionMillisByEpisodeId(2))

            advanceTimeBy(2 * 60 * 60 * 1_000L)
            runCurrent()
            assertEquals(3, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `a collection outage keeps pending edits and waits for a new event`() = runTest {
        var syncRequests = 0
        val client = mockCloud { request ->
            when (request.url.encodedPath) {
                "/healthz" -> respondJson("""{"ok":true,"apiVersion":1,"serverTimeMillis":100}""")
                "/api/v1/collections/sync" -> {
                    syncRequests += 1
                    respondJson("""{"error":"collection_storage_unavailable"}""", HttpStatusCode.ServiceUnavailable)
                }
                else -> error("Unexpected URL ${request.url}")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val database = createTestWynimeDatabase(dispatcher)
        try {
            val store = MemoryDataStore(BangumiCollectionSyncState(accountKey = "id:42"))
            val syncer = BangumiCollectionSyncer(
                pendingStore = store,
                cloudClient = WynimeCloudClient(client.asScopedHttpClient()),
                tokenRepository = tokens,
                subjectCollectionDao = database.subjectCollection(),
                episodeCollectionDao = database.episodeCollection(),
                deviceIdProvider = { "phone" },
                scope = backgroundScope,
                sessionStateProvider = session,
                accountKeyProvider = { "id:42" },
            ).also { it.start() }
            syncer.setForeground(true)
            runCurrent()
            assertEquals(1, syncRequests)

            syncer.enqueueSubject(BangumiSubjectCollectionChange(10, 100, changedFields = listOf("rate"), rate = 8))
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, syncRequests)
            assertEquals(8, store.data.first().changes.single().rate)

            advanceTimeBy(2 * 60 * 60 * 1_000L)
            runCurrent()
            assertEquals(2, syncRequests)

            syncer.setForeground(false)
            runCurrent()
            syncer.setForeground(true)
            runCurrent()
            assertEquals(3, syncRequests)
            assertEquals(8, store.data.first().changes.single().rate)
        } finally {
            database.close()
            client.close()
        }
    }

    @Test
    fun `flush sends a saved checkpoint and keeps idle exit local`() = runTest {
        var syncRequests = 0
        val client = mockCloud {
            syncRequests += 1
            respondJson("""{"cursor":1}""")
        }
        try {
            val (syncer, repository) = playbackDevice(WynimeCloudClient(client.asScopedHttpClient()))
            syncer.flushPending()
            assertEquals(0, syncRequests)

            val unregister = syncer.registerProgressCheckpoint {
                repository.saveOrUpdate(episodeId = 2, positionMillis = 5_000, subjectId = 10, durationMillis = 100_000)
            }
            syncer.saveCurrentProgress()
            syncer.flushPending()
            unregister()
            runCurrent()
            assertEquals(1, syncRequests)
            assertEquals(0, repository.pendingOpsFlow.first().size)
            assertEquals(5_000L, repository.getPositionMillisByEpisodeId(2))
        } finally {
            client.close()
        }
    }

    @Test
    fun `a playback response from a previous session does not update the local cursor`() = runTest {
        val client = mockCloud {
            tokens.setRefreshToken("replacement-session")
            respondJson("""{"cursor":12,"serverChanges":[{"subjectId":10,"episodeId":2,"positionMs":20000,
                "durationMs":100000,"completed":false,"lastPlayedAt":100,"revision":12}]}""")
        }
        try {
            val (syncer, repository) = playbackDevice(WynimeCloudClient(client.asScopedHttpClient()))
            syncer.syncOnce()
            assertEquals(0L, repository.lastSyncAtMillisFlow.first())
            assertEquals(null, repository.getPositionMillisByEpisodeId(2))
        } finally {
            client.close()
        }
    }

    private fun TestScope.playbackDevice(
        cloudClient: WynimeCloudClient,
    ): Pair<PlaybackHistorySyncer, EpisodePlayHistoryRepositoryImpl> {
        lateinit var syncer: PlaybackHistorySyncer
        val repository = EpisodePlayHistoryRepositoryImpl(
            dataStore = MemoryDataStore(EpisodeHistories.Empty),
            playbackHistoryDao = createMemoryPlaybackHistoryDao(),
            onDirtyChanged = { syncer.requestSync() },
        )
        syncer = PlaybackHistorySyncer(
            repository = repository,
            cloudClient = cloudClient,
            tokenRepository = tokens,
            settingsRepository = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())),
            sessionStateProvider = session,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        syncer.start()
        return syncer to repository
    }

    private fun TestScope.mockCloud(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
        expectSuccess = true
        install(ContentNegotiation) { json() }
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
}
