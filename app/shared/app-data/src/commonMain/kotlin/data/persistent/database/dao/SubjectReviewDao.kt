package com.wynime.app.data.persistent.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.wynime.app.data.persistent.database.entity.SubjectReviewEntity

@Dao
interface SubjectReviewDao {
    @Upsert
    suspend fun upsert(item: SubjectReviewEntity)

    @Upsert
    @Transaction
    suspend fun upsert(item: List<SubjectReviewEntity>)

    @Query(
        """
        SELECT * FROM subject_review 
        WHERE subjectId = :subjectId
        ORDER BY updatedAt DESC
        """,
    )
    fun filterBySubjectIdPager(
        subjectId: Int,
    ): PagingSource<Int, SubjectReviewEntity>
}