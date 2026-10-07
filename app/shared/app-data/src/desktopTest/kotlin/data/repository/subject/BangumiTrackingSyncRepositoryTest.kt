/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.models.subject.SubjectCollectionCounts
import me.him188.ani.app.data.network.BatchSubjectRelations
import me.him188.ani.app.data.network.SubjectService
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.persistent.createTestPreferencesDataStore
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.persistent.database.AniDatabaseConstructor
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity
import me.him188.ani.app.data.repository.RepositoryRequestError
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRecommendation
import me.him188.ani.client.models.AniUpdateSubjectCollectionRequest
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubjectCollectionType
import me.him188.ani.utils.platform.currentTimeMillis
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats

class BangumiTrackingSyncRepositoryTest {
    @Test
    fun `auto sync disabled does not send a remote mutation`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.DOING, 100))
            fixture.metadata.markLocalChange(1, UnifiedCollectionType.DOING, 100)

            fixture.repository.enqueueLocalChange(1)
            advanceUntilIdle()

            assertTrue(fixture.api.upserts.isEmpty())
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `auto sync coalesces rapid changes to the final type`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.settings.setAutoSyncTracking(true)
            assertTrue(fixture.settings.flow.first().autoSyncTracking)
            fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.WISH, 100))
            val event = async { fixture.repository.autoSyncEvents.first() }

            fixture.metadata.markLocalChange(1, UnifiedCollectionType.WISH, 100)
            fixture.repository.enqueueLocalChange(1)
            fixture.metadata.markLocalChange(1, UnifiedCollectionType.DOING, 101)
            fixture.repository.enqueueLocalChange(1)
            fixture.metadata.markLocalChange(1, UnifiedCollectionType.DONE, 102)
            fixture.repository.enqueueLocalChange(1)

            runCurrent()
            advanceTimeBy(501)
            runCurrent()
            assertIs<BangumiTrackingAutoSyncEvent.Succeeded>(event.await())

            assertEquals(listOf(1 to UnifiedCollectionType.DONE), fixture.api.upserts)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `full sync fetches anime collections in pages of one hundred`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            val remote = (1..101).map {
                BangumiTrackingRemoteSnapshot(it, UnifiedCollectionType.WISH, 100)
            }
            fixture.api.page = { offset ->
                BangumiTrackingRemotePage(remote.drop(offset).take(100), remote.size)
            }
            fixture.database.subjectCollection().upsert(
                remote.map { subject(it.subjectId, UnifiedCollectionType.WISH, 100) },
            )

            fixture.repository.syncNow()

            assertEquals(listOf(0 to 100, 100 to 100), fixture.api.pageRequests)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `non empty total with an empty first page fails and leaves a terminal state`() = runTest {
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fixture = fixture(serviceScope)
        try {
            fixture.api.page = { BangumiTrackingRemotePage(emptyList(), 1) }

            val failure = async { runCatching { fixture.repository.syncNow() }.exceptionOrNull() }
            advanceUntilIdle()
            assertIs<RepositoryRequestError>(failure.await())
            assertIs<BangumiSyncUiState.Failed>(fixture.repository.syncState.value)
        } finally {
            serviceScope.cancel()
            fixture.database.close()
        }
    }

    @Test
    fun `missing total continues after a full page until a short page`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            val remote = (1..101).map {
                BangumiTrackingRemoteSnapshot(it, UnifiedCollectionType.WISH, 100)
            }
            fixture.api.page = { offset ->
                BangumiTrackingRemotePage(remote.drop(offset).take(100), null)
            }

            fixture.repository.syncNow()

            assertEquals(listOf(0 to 100, 100 to 100), fixture.api.pageRequests)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `manual sync is single flight`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.api.pageGate = CompletableDeferred()
            val first = async { fixture.repository.syncNow() }
            runCurrent()
            fixture.api.pageStarted.await()

            val second = async { fixture.repository.syncNow() }
            runCurrent()
            fixture.api.pageGate!!.complete(Unit)
            advanceUntilIdle()

            first.await()
            second.await()
            assertEquals(1, fixture.api.currentUserCalls.get())
            assertEquals(listOf(0 to 100), fixture.api.pageRequests)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `full sync limits remote mutations to four concurrent requests`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.api.upsertDelayMillis = 1
            fixture.database.subjectCollection().upsert(
                (1..8).map { subject(it, UnifiedCollectionType.DOING, it.toLong()) },
            )

            fixture.repository.syncNow()
            assertEquals(8, fixture.api.upserts.size)
            assertTrue(fixture.api.maxUpserts.get() <= 4)
        } finally {
            fixture.database.close()
        }
    }

    private data class Fixture(
        val database: AniDatabase,
        val metadata: BangumiTrackingMetadataRepository,
        val settings: BangumiTrackingSyncSettingsStore,
        val api: FakeSyncApi,
        val repository: BangumiTrackingSyncRepository,
    )

    private fun fixture(scope: CoroutineScope): Fixture {
        val database = Room.inMemoryDatabaseBuilder<AniDatabase> { AniDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()
        val settings = BangumiTrackingSyncSettingsStore(createTestPreferencesDataStore())
        val metadata = BangumiTrackingMetadataRepository(
            dao = database.bangumiTrackingMetadataDao(),
            tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial)),
            accountBindingStore = settings,
        )
        val api = FakeSyncApi()
        return Fixture(
            database = database,
            metadata = metadata,
            settings = settings,
            api = api,
            repository = BangumiTrackingSyncRepository(
                subjectCollectionDao = database.subjectCollection(),
                metadataDao = database.bangumiTrackingMetadataDao(),
                metadataRepository = metadata,
                subjectService = EmptySubjectService,
                bangumiApi = api,
                settingsStore = settings,
                serviceScope = scope,
            ),
        )
    }

    private class FakeSyncApi : BangumiTrackingSyncApi {
        val account = BangumiTrackingAccount(1060673, "1060673")
        val currentUserCalls = AtomicInteger()
        val pageRequests = CopyOnWriteArrayList<Pair<Int, Int>>()
        val upserts = CopyOnWriteArrayList<Pair<Int, UnifiedCollectionType>>()
        val inFlightUpserts = AtomicInteger()
        val maxUpserts = AtomicInteger()
        var page: (Int) -> BangumiTrackingRemotePage = { BangumiTrackingRemotePage(emptyList(), 0) }
        var pageGate: CompletableDeferred<Unit>? = null
        val pageStarted = CompletableDeferred<Unit>()
        var upsertDelayMillis: Long = 0

        override suspend fun currentUser(): BangumiTrackingAccount {
            currentUserCalls.incrementAndGet()
            return account
        }

        override suspend fun animeCollections(username: String, limit: Int, offset: Int): BangumiTrackingRemotePage {
            pageRequests += offset to limit
            pageStarted.complete(Unit)
            pageGate?.await()
            return page(offset)
        }

        override suspend fun upsertCollectionType(subjectId: Int, type: UnifiedCollectionType) {
            upserts += subjectId to type
            val inFlight = inFlightUpserts.incrementAndGet()
            maxUpserts.updateAndGet { maxOf(it, inFlight) }
            try {
                if (upsertDelayMillis > 0) delay(upsertDelayMillis)
            } finally {
                inFlightUpserts.decrementAndGet()
            }
        }
    }

    private object EmptySubjectService : SubjectService {
        override suspend fun getSubjectCollections(
            type: BangumiSubjectCollectionType?,
            offset: Int,
            limit: Int,
        ): List<AniSubjectCollection> = emptyList()

        override suspend fun getSubjectCollection(subjectId: Int): AniSubjectCollection? = null

        override suspend fun getSubjectRelations(subjectId: Int, withCharacterActors: Boolean): BatchSubjectRelations =
            error("not needed")

        override fun subjectCollectionById(subjectId: Int): Flow<AniSubjectCollection?> = flow { emit(null) }

        override suspend fun patchSubjectCollection(subjectId: Int, payload: AniUpdateSubjectCollectionRequest) =
            error("not needed")

        override suspend fun getSubjectRecommendations(subjectId: Int, limit: Int): List<AniSubjectRecommendation> =
            error("not needed")

        override fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts> = error("not needed")

        override suspend fun performBangumiFullSync() = error("not needed")

        override suspend fun getBangumiFullSyncState(): BangumiSyncState? = error("not needed")
    }

    private fun subject(subjectId: Int, type: UnifiedCollectionType, updatedAt: Long) = SubjectCollectionEntity(
        subjectId = subjectId,
        name = "subject-$subjectId",
        nameCn = "Subject $subjectId",
        summary = "",
        nsfw = false,
        imageLarge = "",
        imageThumb = "",
        totalEpisodes = 0,
        airDate = PackedDate.Invalid,
        aliases = emptyList(),
        tags = emptyList(),
        collectionStats = SubjectCollectionStats.Zero,
        ratingInfo = RatingInfo.Empty,
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo(score = 0, comment = null, tags = emptyList(), isPrivate = false),
        collectionType = type,
        recurrence = null,
        lastUpdated = updatedAt,
        lastFetched = currentTimeMillis(),
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )
}
