package com.wynime.app.data.repository.subject

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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.subject.SubjectCollectionCounts
import com.wynime.app.data.network.BatchSubjectRelations
import com.wynime.app.data.network.SubjectService
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.persistent.createTestPreferencesDataStore
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.persistent.database.WynimeDatabaseConstructor
import com.wynime.app.data.persistent.database.dao.SubjectCollectionEntity
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.TokenSave
import com.wynime.models.SubjectCollectionDto
import com.wynime.models.SubjectRecommendationDto
import com.wynime.models.UpdateSubjectCollectionRequestDto
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.bangumi.models.BangumiSubjectCollectionType
import com.wynime.utils.platform.currentTimeMillis
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.data.models.subject.SubjectCollectionStats

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
            advanceUntilIdle()
            assertIs<BangumiTrackingAutoSyncEvent.Succeeded>(event.await())

            assertEquals(listOf(1 to UnifiedCollectionType.DONE), fixture.api.upserts)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `local deletion persists as a durable delete operation across repository restart`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.metadata.markLocalChange(1, UnifiedCollectionType.WISH, 100)
            fixture.metadata.markLocalDeletion(1, 200)

            val restartedMetadata = BangumiTrackingMetadataRepository(
                dao = fixture.database.bangumiTrackingMetadataDao(),
                tokenRepository = TokenRepository(MemoryDataStore(TokenSave.Initial)),
                accountBindingStore = fixture.settings,
            )
            val persisted = restartedMetadata.find(1)

            assertEquals(200, persisted?.localDeletedAt)
            assertEquals(BangumiTrackingPendingOperation.DELETE_COLLECTION.name, persisted?.pendingOperation)
            assertNull(persisted?.pendingType)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `verified remote deletion clears the tombstone`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.api.page = {
                BangumiTrackingRemotePage(
                    listOf(BangumiTrackingRemoteSnapshot(1, UnifiedCollectionType.WISH, 100)),
                    1,
                )
            }
            fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.NOT_COLLECTED, 300))
            fixture.metadata.markLocalDeletion(1, 300)

            val result = fixture.repository.syncNow()
            val persisted = fixture.metadata.find(1)

            assertEquals(listOf(1), fixture.api.deletes)
            assertTrue(fixture.api.collectionCalls.get() >= 2)
            assertNull(fixture.api.collection(fixture.api.account.username, 1))
            assertEquals(1, result.deletedRemote)
            assertNull(persisted?.localDeletedAt)
            assertNull(persisted?.pendingOperation)
            assertNull(persisted?.pendingError)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `failed remote deletion keeps the tombstone for retry`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.api.page = {
                BangumiTrackingRemotePage(
                    listOf(BangumiTrackingRemoteSnapshot(1, UnifiedCollectionType.WISH, 100)),
                    1,
                )
            }
            fixture.subjectService.failDelete = true
            fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.NOT_COLLECTED, 300))
            fixture.metadata.markLocalDeletion(1, 300)

            val result = fixture.repository.syncNow()
            val persisted = assertNotNull(fixture.metadata.find(1))

            assertEquals(emptyList(), fixture.api.deletes)
            assertEquals(listOf(1), result.failedSubjectIds)
            assertEquals(0, result.deletedRemote)
            assertEquals(BangumiTrackingPendingOperation.DELETE_COLLECTION.name, persisted.pendingOperation)
            assertEquals(300, persisted.localDeletedAt)
            assertNotNull(persisted.pendingError)
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
    fun `full sync uses the collection list without per-subject detail requests`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            val remote = (1..47).map {
                BangumiTrackingRemoteSnapshot(it, UnifiedCollectionType.WISH, 100)
            }
            fixture.api.page = { BangumiTrackingRemotePage(remote, remote.size) }
            fixture.database.subjectCollection().upsert(
                remote.map { subject(it.subjectId, UnifiedCollectionType.WISH, 100) },
            )

            fixture.repository.syncNow()

            assertEquals(listOf(0 to 100), fixture.api.pageRequests)
            assertEquals(0, fixture.api.collectionCalls.get())
            assertEquals(0, fixture.subjectService.detailCalls.get())
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
    fun `full sync sends independent remote mutations without an application concurrency cap`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.api.upsertDelayMillis = 100
            fixture.database.subjectCollection().upsert(
                (1..8).map { subject(it, UnifiedCollectionType.DOING, it.toLong()) },
            )

            fixture.repository.syncNow()
            assertEquals(8, fixture.api.upserts.size)
            assertTrue(fixture.api.maxUpserts.get() > 4)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `return from web keeps collection and pending operation until remote absence is verified`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.WISH, 100))
            fixture.api.page = { BangumiTrackingRemotePage(listOf(BangumiTrackingRemoteSnapshot(1, UnifiedCollectionType.WISH, 100)), 1) }
            fixture.api.animeCollections(fixture.api.account.username, 100, 0)
            fixture.metadata.markLocalDeletion(1, 300)
            fixture.subjectService.failDelete = true
            fixture.repository.confirmPendingWebRemovals()
            assertEquals(UnifiedCollectionType.WISH, fixture.database.subjectCollection().findById(1).first()?.collectionType)
            assertEquals(BangumiTrackingPendingOperation.DELETE_COLLECTION.name, fixture.metadata.find(1)?.pendingOperation)
            assertNotNull(fixture.metadata.find(1)?.pendingError)
            fixture.api.removeRemote(1)
            fixture.repository.confirmPendingWebRemovals()
            assertEquals(UnifiedCollectionType.NOT_COLLECTED, fixture.database.subjectCollection().findById(1).first()?.collectionType)
            assertNull(fixture.metadata.find(1)?.pendingOperation)
            assertTrue(fixture.api.deletes.isEmpty())
            assertTrue(fixture.api.collectionCalls.get() >= 3)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `foreground confirmation does not flush ordinary changes with auto sync disabled`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.WISH, 100))
            fixture.metadata.markLocalChange(1, UnifiedCollectionType.WISH, 100)
            fixture.repository.confirmPendingWebRemovals()
            assertEquals(0, fixture.api.currentUserCalls.get())
            assertTrue(fixture.api.upserts.isEmpty())
            assertEquals(BangumiTrackingPendingOperation.UPSERT_COLLECTION.name, fixture.metadata.find(1)?.pendingOperation)
        } finally {
            fixture.database.close()
        }
    }

    @Test
    fun `account switch during removal query preserves both accounts pending operations`() = runTest {
        val fixture = fixture(backgroundScope)
        try {
            fixture.settings.update { copy(autoSyncTracking = false) }
            val oldAccount = fixture.api.account
            fixture.metadata.adoptAccount(oldAccount)
            fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.WISH, 100))
            fixture.metadata.markLocalDeletion(1, 200)
            fixture.api.onCollection = {
                fixture.api.onCollection = null
                fixture.tokens.setRefreshToken("other-account-session")
                fixture.api.account = BangumiTrackingAccount(200, "other")
                fixture.metadata.adoptAccount(fixture.api.account)
                fixture.metadata.markLocalChange(1, UnifiedCollectionType.DOING, 300)
                fixture.database.subjectCollection().upsert(subject(1, UnifiedCollectionType.DOING, 300))
            }
            fixture.repository.confirmPendingWebRemovals()
            val dao = fixture.database.bangumiTrackingMetadataDao()
            assertEquals(BangumiTrackingPendingOperation.DELETE_COLLECTION.name, dao.find(oldAccount.key, 1)?.pendingOperation)
            assertNotNull(dao.find(oldAccount.key, 1)?.pendingError)
            assertEquals(BangumiTrackingPendingOperation.UPSERT_COLLECTION.name, dao.find(fixture.api.account.key, 1)?.pendingOperation)
            assertEquals(null, dao.find(fixture.api.account.key, 1)?.pendingError)
            assertEquals(UnifiedCollectionType.DOING, fixture.database.subjectCollection().findById(1).first()?.collectionType)
            assertTrue(fixture.api.deletes.isEmpty())
        } finally {
            fixture.database.close()
        }
    }

    private data class Fixture(
        val database: WynimeDatabase,
        val tokens: TokenRepository,
        val metadata: BangumiTrackingMetadataRepository,
        val settings: BangumiTrackingSyncSettingsStore,
        val api: FakeSyncApi,
        val subjectService: FakeSubjectService,
        val repository: BangumiTrackingSyncRepository,
    )

    private fun fixture(scope: CoroutineScope): Fixture {
        val database = Room.inMemoryDatabaseBuilder<WynimeDatabase> { WynimeDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .build()
        val settings = BangumiTrackingSyncSettingsStore(createTestPreferencesDataStore())
        val tokens = TokenRepository(MemoryDataStore(TokenSave.Initial))
        val metadata = BangumiTrackingMetadataRepository(
            dao = database.bangumiTrackingMetadataDao(),
            tokenRepository = tokens,
            accountBindingStore = settings,
        )
        val api = FakeSyncApi()
        val subjectService = FakeSubjectService(api)
        return Fixture(
            database = database,
            tokens = tokens,
            metadata = metadata,
            settings = settings,
            api = api,
            subjectService = subjectService,
            repository = BangumiTrackingSyncRepository(
                subjectCollectionDao = database.subjectCollection(),
                metadataDao = database.bangumiTrackingMetadataDao(),
                metadataRepository = metadata,
                subjectService = subjectService,
                bangumiApi = api,
                settingsStore = settings,
                serviceScope = scope,
            ),
        )
    }

    private class FakeSyncApi : BangumiTrackingSyncApi {
        var account = BangumiTrackingAccount(1060673, "1060673")
        var onCollection: (suspend () -> Unit)? = null
        val currentUserCalls = AtomicInteger()
        val pageRequests = CopyOnWriteArrayList<Pair<Int, Int>>()
        val upserts = CopyOnWriteArrayList<Pair<Int, UnifiedCollectionType>>()
        val deletes = CopyOnWriteArrayList<Int>()
        val collectionCalls = AtomicInteger()
        private val remoteCollections = java.util.concurrent.ConcurrentHashMap<Int, BangumiTrackingRemoteSnapshot>()
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
            return page(offset).also { result ->
                result.collections.forEach { remoteCollections[it.subjectId] = it }
            }
        }

        override suspend fun collection(username: String, subjectId: Int): BangumiTrackingRemoteSnapshot? {
            collectionCalls.incrementAndGet()
            onCollection?.invoke()
            return remoteCollections[subjectId]
        }

        fun removeRemote(subjectId: Int) {
            remoteCollections.remove(subjectId)
        }

        override suspend fun upsertCollectionType(subjectId: Int, type: UnifiedCollectionType) {
            upserts += subjectId to type
            remoteCollections[subjectId] = BangumiTrackingRemoteSnapshot(
                subjectId = subjectId,
                type = type,
                updatedAt = currentTimeMillis(),
            )
            val inFlight = inFlightUpserts.incrementAndGet()
            maxUpserts.updateAndGet { maxOf(it, inFlight) }
            try {
                if (upsertDelayMillis > 0) delay(upsertDelayMillis)
            } finally {
                inFlightUpserts.decrementAndGet()
            }
        }
    }

    private class FakeSubjectService(
        private val api: FakeSyncApi,
    ) : SubjectService {
        val detailCalls = AtomicInteger()
        var failDelete = false

        override suspend fun getSubjectCollections(
            type: BangumiSubjectCollectionType?,
            offset: Int,
            limit: Int,
        ): List<SubjectCollectionDto> = emptyList()

        override suspend fun getSubjectCollection(subjectId: Int): SubjectCollectionDto? {
            detailCalls.incrementAndGet()
            return null
        }

        override suspend fun getSubjectRelations(subjectId: Int, withCharacterActors: Boolean): BatchSubjectRelations =
            error("not needed")

        override fun subjectCollectionById(subjectId: Int): Flow<SubjectCollectionDto?> = flow { emit(null) }

        override suspend fun patchSubjectCollection(subjectId: Int, payload: UpdateSubjectCollectionRequestDto) =
            error("not needed")

        override suspend fun deleteSubjectCollection(subjectId: Int) {
            if (failDelete) throw RepositoryRequestError("simulated DELETE failure")
            api.deletes += subjectId
            api.removeRemote(subjectId)
        }

        override suspend fun getSubjectRecommendations(subjectId: Int, limit: Int): List<SubjectRecommendationDto> =
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
