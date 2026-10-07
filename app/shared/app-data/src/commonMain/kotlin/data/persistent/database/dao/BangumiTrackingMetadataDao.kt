/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Local metadata for the Bangumi tracking synchronizer.
 *
 * A row with [localDeletedAt] set is a durable local tombstone. Rows are scoped by
 * [accountKey] so switching Bangumi accounts never reuses another account's decision.
 */
@Entity(
    tableName = "bangumi_tracking_metadata",
    primaryKeys = ["accountKey", "subjectId"],
    indices = [Index(value = ["accountKey", "localDeletedAt"])],
)
data class BangumiTrackingMetadataEntity(
    val accountKey: String,
    val subjectId: Int,
    val localDeletedAt: Long? = null,
    val lastLocalModifiedAt: Long = 0,
    val lastSyncedAt: Long = 0,
    val remoteUpdatedAt: Long? = null,
    val pendingType: String? = null,
    val pendingError: String? = null,
    /** Remote collection state captured by the last verified reconciliation; null means absent. */
    val lastSyncedType: String? = null,
    /** UPSERT_COLLECTION or DELETE_COLLECTION. Kept as data so mutations survive process death. */
    val pendingOperation: String? = null,
)

@Entity(tableName = "bangumi_tracking_account")
data class BangumiTrackingAccountEntity(
    @PrimaryKey val accountKey: String,
    val userId: Int? = null,
    val username: String? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val localCount: Int = 0,
    val remoteCount: Int = 0,
)

@Dao
interface BangumiTrackingMetadataDao {
    @Query(
        "SELECT * FROM bangumi_tracking_metadata WHERE accountKey = :accountKey AND subjectId = :subjectId",
    )
    suspend fun find(accountKey: String, subjectId: Int): BangumiTrackingMetadataEntity?

    @Query("SELECT * FROM bangumi_tracking_metadata WHERE accountKey = :accountKey")
    suspend fun list(accountKey: String): List<BangumiTrackingMetadataEntity>

    @Query("SELECT * FROM bangumi_tracking_metadata WHERE accountKey = :accountKey")
    fun observe(accountKey: String): Flow<List<BangumiTrackingMetadataEntity>>

    @Upsert
    suspend fun upsert(metadata: BangumiTrackingMetadataEntity)

    @Upsert
    suspend fun upsertAll(metadata: List<BangumiTrackingMetadataEntity>)

    @Query(
        "DELETE FROM bangumi_tracking_metadata WHERE accountKey = :accountKey AND subjectId = :subjectId",
    )
    suspend fun delete(accountKey: String, subjectId: Int)

    @Query("DELETE FROM bangumi_tracking_metadata WHERE accountKey = :accountKey")
    suspend fun deleteAll(accountKey: String)

    @Query("SELECT * FROM bangumi_tracking_account WHERE accountKey = :accountKey")
    suspend fun findAccount(accountKey: String): BangumiTrackingAccountEntity?

    @Query("SELECT * FROM bangumi_tracking_account WHERE userId = :userId LIMIT 1")
    suspend fun findAccountByUserId(userId: Int): BangumiTrackingAccountEntity?

    @Upsert
    suspend fun upsertAccount(account: BangumiTrackingAccountEntity)

    @Query("DELETE FROM bangumi_tracking_account WHERE accountKey = :accountKey")
    suspend fun deleteAccount(accountKey: String)
}
