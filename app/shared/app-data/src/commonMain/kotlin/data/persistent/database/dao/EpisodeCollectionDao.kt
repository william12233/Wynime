package com.wynime.app.data.persistent.database.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.persistent.database.converters.PackedDateConverter
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Entity(
    tableName = "episode_collection",
    foreignKeys = [
        ForeignKey(
            entity = SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subjectId", "episodeId"], unique = true),
        Index(value = ["sortNumber", "sort"], orders = [Index.Order.ASC, Index.Order.ASC]),

    ],
)
@TypeConverters(PackedDateConverter::class)
data class EpisodeCollectionEntity(
    val subjectId: Int,
    @PrimaryKey val episodeId: Int,

    val episodeType: EpisodeType?,
    val name: String,
    val nameCn: String,
    val airDate: PackedDate,
    @Deprecated("Not available anymore")
    val comment: Int,
    val desc: String,
    val sort: EpisodeSort,

    @ColumnInfo(defaultValue = "3.4028235e38")
    val sortNumber: Float,
    val ep: EpisodeSort? = null,

    val imageMedium: String? = null,

    val imageLarge: String? = null,

    val selfCollectionType: UnifiedCollectionType,

    val lastFetched: Long,
)

@Dao
interface EpisodeCollectionDao {
    @Query(
        """
        SELECT * FROM episode_collection 
        WHERE episodeId = :episodeId 
        ORDER BY sort DESC
        LIMIT 1
        """,
    )
    fun findByEpisodeId(episodeId: Int): Flow<EpisodeCollectionEntity?>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId
        AND (episodeType = :episodeType)
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun filterBySubjectId(
        subjectId: Int,
        episodeType: EpisodeType,
    ): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId
        AND (episodeType IN (:episodeTypes))
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun filterBySubjectId(
        subjectId: Int,
        episodeTypes: List<EpisodeType>,
    ): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun filterBySubjectId(
        subjectId: Int,
    ): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT episodeId FROM episode_collection
        WHERE subjectId = :subjectId
        ORDER BY sortNumber ASC, sort ASC
        """,
    )
    fun listIdBySubjectId(
        subjectId: Int,
    ): Flow<List<Int>>

    @Query(
        """
        SELECT * FROM episode_collection
        WHERE subjectId = :subjectId 
        ORDER BY sortNumber ASC, sort ASC""",
    )
    fun filterBySubjectIdPaging(subjectId: Int): PagingSource<Int, EpisodeCollectionEntity>

    @Upsert
    suspend fun upsert(item: EpisodeCollectionEntity)

    @Upsert
    @Transaction
    suspend fun upsert(item: List<EpisodeCollectionEntity>)

    @Query("""UPDATE episode_collection SET selfCollectionType = :type WHERE subjectId = :subjectId AND episodeId = :episodeId""")
    suspend fun updateSelfCollectionType(
        subjectId: Int,
        episodeId: Int,
        type: UnifiedCollectionType,
    )

    @Query("""UPDATE episode_collection SET selfCollectionType = :type WHERE subjectId = :subjectId""")
    suspend fun setAllEpisodesWatched(
        subjectId: Int,
        type: UnifiedCollectionType = UnifiedCollectionType.DONE,
    )

    @Query("""select * from episode_collection ORDER BY sortNumber ASC, sort ASC""")
    fun all(): Flow<List<EpisodeCollectionEntity>>

    @Query(
        """
        SELECT lastFetched FROM episode_collection 
        WHERE subjectId = :subjectId
        ORDER BY lastFetched DESC LIMIT 1""",
    )
    suspend fun lastFetched(subjectId: Int): Long

    @Query(
        """
        DELETE FROM episode_collection 
        WHERE subjectId = :subjectId
        """,
    )
    suspend fun deleteAllBySubjectId(subjectId: Int)

    @Query(
        """
        DELETE FROM episode_collection 
        WHERE subjectId = :subjectId AND episodeId IN (:episodeIds)
        """,
    )
    suspend fun deleteAllByEpisodeIds(subjectId: Int, episodeIds: List<Int>)
}

fun EpisodeCollectionDao.filterBySubjectId(
    subjectId: Int,
    episodeType: EpisodeType? = null,
) = if (episodeType == null) {
    filterBySubjectId(subjectId)
} else {
    filterBySubjectId(subjectId, episodeType)
}

