package com.wynime.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wynime.app.data.persistent.database.entity.EpisodeCommentEntity

@Dao
interface EpisodeCommentDao {
    @Upsert
    suspend fun upsert(item: EpisodeCommentEntity)

    @Upsert
    suspend fun upsert(item: List<EpisodeCommentEntity>)

    @Query(
        """
        SELECT * FROM episode_comment 
        WHERE episodeId = :episodeId
          AND parentCommentId IS NULL
        ORDER BY createdAt DESC
        """,
    )
    suspend fun findTopLevelByEpisodeId(
        episodeId: Long,
    ): List<EpisodeCommentEntity>

    @Query(
        """
        SELECT * FROM episode_comment
        WHERE parentCommentId IN (:parentCommentIds)
        ORDER BY createdAt ASC
        """,
    )
    suspend fun findRepliesByParentCommentIds(
        parentCommentIds: List<String>,
    ): List<EpisodeCommentEntity>
}
