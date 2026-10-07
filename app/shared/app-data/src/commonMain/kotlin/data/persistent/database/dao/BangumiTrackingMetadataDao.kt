package com.wynime.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

    val lastSyncedType: String? = null,

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
