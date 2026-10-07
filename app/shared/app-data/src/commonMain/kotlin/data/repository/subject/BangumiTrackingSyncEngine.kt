/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import me.him188.ani.datasources.api.topic.UnifiedCollectionType

enum class BangumiTrackingConflictPolicy {
    LOCAL_FIRST,
    BANGUMI_FIRST,
    LATEST_WINS,
}

data class BangumiTrackingLocalSnapshot(
    val subjectId: Int,
    val type: UnifiedCollectionType,
    val lastUpdated: Long,
)

data class BangumiTrackingRemoteSnapshot(
    val subjectId: Int,
    val type: UnifiedCollectionType,
    val updatedAt: Long,
)

data class BangumiTrackingTombstoneSnapshot(
    val deletedAt: Long,
)

data class BangumiTrackingBaselineSnapshot(
    val type: UnifiedCollectionType?,
    val syncedAt: Long,
    val remoteUpdatedAt: Long?,
)

sealed interface BangumiTrackingSyncAction {
    data object NoOp : BangumiTrackingSyncAction
    data class UpsertRemote(val type: UnifiedCollectionType) : BangumiTrackingSyncAction
    data class ApplyRemote(val type: UnifiedCollectionType) : BangumiTrackingSyncAction
    data object DeleteRemote : BangumiTrackingSyncAction
    data object MarkLocalUntracked : BangumiTrackingSyncAction
    data object Conflict : BangumiTrackingSyncAction
}

data class BangumiTrackingSyncPlan(
    val subjectId: Int,
    val action: BangumiTrackingSyncAction,
    val conflict: Boolean,
)

/**
 * Pure conflict resolution for subject collection types. It deliberately has no network or
 * database dependency so every policy can be tested with deterministic timestamps.
 */
object BangumiTrackingSyncEngine {
    fun plan(
        policy: BangumiTrackingConflictPolicy,
        local: BangumiTrackingLocalSnapshot?,
        remote: BangumiTrackingRemoteSnapshot?,
        tombstone: BangumiTrackingTombstoneSnapshot?,
        baseline: BangumiTrackingBaselineSnapshot? = null,
    ): BangumiTrackingSyncPlan {
        val subjectId = local?.subjectId ?: remote?.subjectId ?: error("A sync plan needs a subject id")
        val localTracked = local?.takeUnless { it.type == UnifiedCollectionType.NOT_COLLECTED }

        val action = when (policy) {
            BangumiTrackingConflictPolicy.LOCAL_FIRST -> when {
                tombstone != null -> BangumiTrackingSyncAction.DeleteRemote

                localTracked != null && localTracked.type != remote?.type ->
                    BangumiTrackingSyncAction.UpsertRemote(localTracked.type)

                localTracked != null && remote == null ->
                    BangumiTrackingSyncAction.UpsertRemote(localTracked.type)

                localTracked == null && remote != null ->
                    BangumiTrackingSyncAction.ApplyRemote(remote.type)

                else -> BangumiTrackingSyncAction.NoOp
            }

            BangumiTrackingConflictPolicy.BANGUMI_FIRST -> when {
                remote != null && remote.type != localTracked?.type ->
                    BangumiTrackingSyncAction.ApplyRemote(remote.type)

                remote == null && localTracked != null ->
                    BangumiTrackingSyncAction.MarkLocalUntracked

                else -> BangumiTrackingSyncAction.NoOp
            }

            BangumiTrackingConflictPolicy.LATEST_WINS -> latestWins(
                local = localTracked,
                remote = remote,
                tombstone = tombstone,
                baseline = baseline,
            )
        }

        return BangumiTrackingSyncPlan(
            subjectId = subjectId,
            action = action,
            conflict = action is BangumiTrackingSyncAction.Conflict ||
                (localTracked != null && remote != null && localTracked.type != remote.type),
        )
    }

    private fun latestWins(
        local: BangumiTrackingLocalSnapshot?,
        remote: BangumiTrackingRemoteSnapshot?,
        tombstone: BangumiTrackingTombstoneSnapshot?,
        baseline: BangumiTrackingBaselineSnapshot?,
    ): BangumiTrackingSyncAction {
        if (local == null && remote == null) return BangumiTrackingSyncAction.NoOp

        if (tombstone != null) {
            if (remote == null || tombstone.deletedAt > remote.updatedAt) {
                return BangumiTrackingSyncAction.DeleteRemote
            }
            if (tombstone.deletedAt < remote.updatedAt) {
                return BangumiTrackingSyncAction.ApplyRemote(remote.type)
            }
            return BangumiTrackingSyncAction.Conflict
        }

        if (local == null) return BangumiTrackingSyncAction.ApplyRemote(remote!!.type)
        if (remote == null) {
            val localChangedAfterBaseline = baseline == null ||
                baseline.type != local.type ||
                local.lastUpdated > baseline.syncedAt
            return if (localChangedAfterBaseline) {
                BangumiTrackingSyncAction.UpsertRemote(local.type)
            } else {
                // A collection that was present in the baseline and disappeared remotely has no
                // remote timestamp. The baseline is the evidence that Bangumi won this change.
                BangumiTrackingSyncAction.MarkLocalUntracked
            }
        }

        if (local.type == remote.type) return BangumiTrackingSyncAction.NoOp

        return when {
            local.lastUpdated > remote.updatedAt -> BangumiTrackingSyncAction.UpsertRemote(local.type)
            local.lastUpdated < remote.updatedAt -> BangumiTrackingSyncAction.ApplyRemote(remote.type)
            // Equal timestamps cannot establish a winner. Preserve the conflict instead of
            // overwriting either side, including when no baseline exists.
            else -> BangumiTrackingSyncAction.Conflict
        }
    }
}
