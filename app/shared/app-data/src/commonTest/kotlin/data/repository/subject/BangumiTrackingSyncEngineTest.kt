package me.him188.ani.app.data.repository.subject

import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BangumiTrackingSyncEngineTest {
    private fun local(type: UnifiedCollectionType, updatedAt: Long = 100): BangumiTrackingLocalSnapshot =
        BangumiTrackingLocalSnapshot(1, type, updatedAt)

    private fun remote(type: UnifiedCollectionType, updatedAt: Long = 100): BangumiTrackingRemoteSnapshot =
        BangumiTrackingRemoteSnapshot(1, type, updatedAt)

    @Test
    fun localFirstPushesLocalType() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LOCAL_FIRST,
            local(UnifiedCollectionType.DOING),
            remote(UnifiedCollectionType.WISH),
            null,
        )
        assertEquals(BangumiTrackingSyncAction.UpsertRemote(UnifiedCollectionType.DOING), plan.action)
    }

    @Test
    fun bangumiFirstAppliesRemoteType() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.BANGUMI_FIRST,
            local(UnifiedCollectionType.WISH),
            remote(UnifiedCollectionType.DOING),
            null,
        )
        assertEquals(BangumiTrackingSyncAction.ApplyRemote(UnifiedCollectionType.DOING), plan.action)
    }

    @Test
    fun bangumiFirstRemovesLocalOnlyCollectionWithoutDeleteRequest() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.BANGUMI_FIRST,
            local(UnifiedCollectionType.WISH),
            null,
            null,
        )
        assertEquals(BangumiTrackingSyncAction.MarkLocalUntracked, plan.action)
    }

    @Test
    fun latestWinsWhenLocalIsNewer() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LATEST_WINS,
            local(UnifiedCollectionType.DONE, updatedAt = 200),
            remote(UnifiedCollectionType.WISH, updatedAt = 100),
            null,
        )
        assertEquals(BangumiTrackingSyncAction.UpsertRemote(UnifiedCollectionType.DONE), plan.action)
    }

    @Test
    fun latestWinsWhenRemoteIsNewer() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LATEST_WINS,
            local(UnifiedCollectionType.DONE, updatedAt = 100),
            remote(UnifiedCollectionType.WISH, updatedAt = 200),
            null,
        )
        assertEquals(BangumiTrackingSyncAction.ApplyRemote(UnifiedCollectionType.WISH), plan.action)
    }

    @Test
    fun newerTombstonePlansTrueRemoteDelete() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LATEST_WINS,
            local(UnifiedCollectionType.NOT_COLLECTED, updatedAt = 300),
            remote(UnifiedCollectionType.DOING, updatedAt = 200),
            BangumiTrackingTombstoneSnapshot(300),
        )
        assertEquals(BangumiTrackingSyncAction.DeleteRemote, plan.action)
        assertEquals(false, plan.conflict)
    }

    @Test
    fun remoteUpdateAfterTombstoneRestoresCollection() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LATEST_WINS,
            local(UnifiedCollectionType.NOT_COLLECTED, updatedAt = 100),
            remote(UnifiedCollectionType.DOING, updatedAt = 300),
            BangumiTrackingTombstoneSnapshot(200),
        )
        assertEquals(BangumiTrackingSyncAction.ApplyRemote(UnifiedCollectionType.DOING), plan.action)
    }

    @Test
    fun equalTimestampIsAnUnresolvedConflict() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LATEST_WINS,
            local(UnifiedCollectionType.DONE, updatedAt = 100),
            remote(UnifiedCollectionType.WISH, updatedAt = 100),
            null,
        )
        assertEquals(BangumiTrackingSyncAction.Conflict, plan.action)
        assertEquals(true, plan.conflict)
    }

    @Test
    fun latestWinsUsesBaselineWhenRemoteDeleteHasNoTimestamp() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LATEST_WINS,
            local(UnifiedCollectionType.WISH, updatedAt = 100),
            null,
            null,
            BangumiTrackingBaselineSnapshot(
                type = UnifiedCollectionType.WISH,
                syncedAt = 200,
                remoteUpdatedAt = 100,
            ),
        )
        assertEquals(BangumiTrackingSyncAction.MarkLocalUntracked, plan.action)
    }

    @Test
    fun latestWinsLocalChangeBeatsRemoteAbsence() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LATEST_WINS,
            local(UnifiedCollectionType.WISH, updatedAt = 300),
            null,
            null,
            BangumiTrackingBaselineSnapshot(
                type = UnifiedCollectionType.WISH,
                syncedAt = 200,
                remoteUpdatedAt = 100,
            ),
        )
        assertEquals(BangumiTrackingSyncAction.UpsertRemote(UnifiedCollectionType.WISH), plan.action)
    }

    @Test
    fun droppedAndNotCollectedRemainDifferentStates() {
        val plan = BangumiTrackingSyncEngine.plan(
            BangumiTrackingConflictPolicy.LOCAL_FIRST,
            local(UnifiedCollectionType.NOT_COLLECTED, updatedAt = 300),
            remote(UnifiedCollectionType.DROPPED, updatedAt = 200),
            BangumiTrackingTombstoneSnapshot(300),
        )
        assertEquals(BangumiTrackingSyncAction.DeleteRemote, plan.action)
        assertIs<BangumiTrackingSyncAction.DeleteRemote>(plan.action)
    }

    @Test
    fun authorizationFailuresArePresentedAsLoginExpired() {
        assertEquals(
            BangumiTrackingConnectionError.AUTHORIZATION,
            classifyBangumiTrackingError(RepositoryAuthorizationException("401")),
        )
        assertEquals(
            BangumiTrackingConnectionError.AUTHORIZATION,
            classifyBangumiTrackingError(RepositoryAuthorizationException("403")),
        )
    }

    @Test
    fun rateLimitAndNetworkFailuresKeepDistinctStates() {
        assertEquals(
            BangumiTrackingConnectionError.RATE_LIMITED,
            classifyBangumiTrackingError(RepositoryRateLimitedException("429")),
        )
        assertEquals(
            BangumiTrackingConnectionError.NETWORK,
            classifyBangumiTrackingError(RepositoryNetworkException("offline")),
        )
    }
}
