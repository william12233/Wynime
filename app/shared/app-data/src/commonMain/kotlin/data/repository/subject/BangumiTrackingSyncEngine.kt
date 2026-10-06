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

sealed interface BangumiTrackingSyncAction {
    data object NoOp : BangumiTrackingSyncAction
    data class UpsertRemote(val type: UnifiedCollectionType) : BangumiTrackingSyncAction
    data class ApplyRemote(val type: UnifiedCollectionType) : BangumiTrackingSyncAction
    data object MarkLocalUntracked : BangumiTrackingSyncAction
    data object KeepLocalUntracked : BangumiTrackingSyncAction
}

data class BangumiTrackingSyncPlan(
    val subjectId: Int,
    val action: BangumiTrackingSyncAction,
    val conflict: Boolean,
    val remoteDeleteUnsupported: Boolean,
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
    ): BangumiTrackingSyncPlan {
        val subjectId = local?.subjectId ?: remote?.subjectId ?: error("A sync plan needs a subject id")
        val localTracked = local?.takeUnless { it.type == UnifiedCollectionType.NOT_COLLECTED }

        if (tombstone != null && (remote == null || remote.updatedAt <= tombstone.deletedAt)) {
            return BangumiTrackingSyncPlan(
                subjectId = subjectId,
                action = BangumiTrackingSyncAction.KeepLocalUntracked,
                conflict = remote != null,
                remoteDeleteUnsupported = remote != null,
            )
        }

        val action = when (policy) {
            BangumiTrackingConflictPolicy.LOCAL_FIRST -> when {
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
            )
        }

        return BangumiTrackingSyncPlan(
            subjectId = subjectId,
            action = action,
            conflict = localTracked != null && remote != null && localTracked.type != remote.type,
            remoteDeleteUnsupported = false,
        )
    }

    private fun latestWins(
        local: BangumiTrackingLocalSnapshot?,
        remote: BangumiTrackingRemoteSnapshot?,
        tombstone: BangumiTrackingTombstoneSnapshot?,
    ): BangumiTrackingSyncAction {
        if (local == null && remote == null) return BangumiTrackingSyncAction.NoOp
        if (local == null) return BangumiTrackingSyncAction.ApplyRemote(remote!!.type)
        if (remote == null) return BangumiTrackingSyncAction.UpsertRemote(local.type)

        val localUpdatedAt = maxOf(local.lastUpdated, tombstone?.deletedAt ?: Long.MIN_VALUE)
        return if (localUpdatedAt >= remote.updatedAt) {
            if (local.type == remote.type) BangumiTrackingSyncAction.NoOp
            else BangumiTrackingSyncAction.UpsertRemote(local.type)
        } else {
            BangumiTrackingSyncAction.ApplyRemote(remote.type)
        }
    }
}
