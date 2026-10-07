package com.wynime.app.data.persistent.database.dao

import androidx.compose.runtime.Immutable
import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.schedule.AnimeRecurrence
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectTmdbArt
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.data.persistent.database.ProtoConverters
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.currentTimeMillis
import kotlin.time.Duration.Companion.hours

@Entity(
    tableName = "subject_collection",
    indices = [
        Index(value = ["lastUpdated"], unique = false, orders = [Index.Order.DESC]),
    ],
)
data class SubjectCollectionEntity(
    @PrimaryKey val subjectId: Int,

    val name: String,
    val nameCn: String,
    val summary: String,
    val nsfw: Boolean,
    val imageLarge: String,

    @ColumnInfo(defaultValue = "")
    val imageThumb: String = "",

    val totalEpisodes: Int,
    val airDate: PackedDate,
    @field:TypeConverters(ProtoConverters.StringList::class)
    val aliases: List<String>,
    @field:TypeConverters(ProtoConverters.TagList::class)
    val tags: List<Tag>,
    @Embedded(prefix = "collection_stats_")
    val collectionStats: SubjectCollectionStats,
    @Embedded(prefix = "rating_")
    val ratingInfo: RatingInfo,
    val completeDate: PackedDate,

    @Embedded(prefix = "self_rating_")
    val selfRatingInfo: SelfRatingInfo,
    val collectionType: UnifiedCollectionType,

    @Embedded(prefix = "recurrence_")
    val recurrence: AnimeRecurrence?,

    @Embedded(prefix = "relations_")
    val relations: SubjectRelations = SubjectRelations.Empty,

    @field:TypeConverters(ProtoConverters.SubjectTmdbArtConverter::class)
    val tmdbArt: SubjectTmdbArt? = null,

    @ColumnInfo(defaultValue = "0")
    val lastUpdated: Long,

    @ColumnInfo(defaultValue = "0")
    val lastFetched: Long,
    @ColumnInfo(defaultValue = "0")
    val cachedStaffUpdated: Long,
    @ColumnInfo(defaultValue = "0")
    val cachedCharactersUpdated: Long,
)

@Immutable
data class SubjectRelations(
    @ColumnInfo(defaultValue = "'[]'")
    @field:TypeConverters(ProtoConverters.IntList::class)
    val seriesMainSubjectIds: List<Int>,
    @ColumnInfo(defaultValue = "'[]'")
    @field:TypeConverters(ProtoConverters.StringList::class)
    val seriesMainSubjectNames: List<String>,
    @ColumnInfo(defaultValue = "'[]'")
    @field:TypeConverters(ProtoConverters.IntList::class)
    val sequelSubjects: List<Int>,
    @ColumnInfo(defaultValue = "'[]'")
    @field:TypeConverters(ProtoConverters.StringList::class)
    val sequelSubjectNames: List<String>,
) {
    companion object {
        val Empty = SubjectRelations(
            seriesMainSubjectIds = emptyList(),
            seriesMainSubjectNames = emptyList(),
            sequelSubjects = emptyList(),
            sequelSubjectNames = emptyList(),
        )
    }
}

@Dao
interface SubjectCollectionDao {
    @Upsert
    suspend fun upsert(item: SubjectCollectionEntity)

    @Upsert
    @Transaction
    suspend fun upsert(item: List<SubjectCollectionEntity>)

    @Query("""UPDATE subject_collection SET collectionType = :collectionType, lastUpdated = :lastUpdated, lastFetched = :lastFetched WHERE subjectId = :subjectId""")
    suspend fun updateType(
        subjectId: Int,
        collectionType: UnifiedCollectionType,
        lastUpdated: Long = currentTimeMillis(),
        lastFetched: Long = currentTimeMillis(),
    ): Int

    @Query("""DELETE FROM subject_collection WHERE subjectId = :subjectId""")
    suspend fun delete(subjectId: Int)

    @Query("""DELETE FROM subject_collection WHERE subjectId IN (:subjectIds)""")
    suspend fun deleteByIds(subjectIds: List<Int>)

    @Query("""UPDATE subject_collection SET lastFetched = 0""")
    suspend fun resetAllLastFetched()

    @Query("""DELETE FROM subject_collection WHERE collectionType = :type""")
    suspend fun deleteAll(type: UnifiedCollectionType)

    @Query("""SELECT subjectId FROM subject_collection WHERE collectionType = :type""")
    suspend fun listIdsByCollectionType(type: UnifiedCollectionType): List<Int>

    @Query("""DELETE FROM subject_collection WHERE collectionType != 'NOT_COLLECTED'""")
    suspend fun deleteAll()

    @Query(
        """
    SELECT * FROM subject_collection 
    WHERE collectionType IS NOT NULL 
    AND collectionType != 'NOT_COLLECTED'
    AND (collectionType IN (:collectionTypes))
    ORDER BY lastUpdated DESC
    LIMIT :limit
    OFFSET :offset
    """,
    )
    fun filterMostRecentUpdated(
        collectionTypes: List<UnifiedCollectionType>,
        limit: Int,
        offset: Int = 0,
    ): Flow<List<SubjectCollectionEntity>>

    @Query(
        """
    SELECT * FROM subject_collection 
    WHERE collectionType IS NOT NULL 
    AND collectionType != 'NOT_COLLECTED'
    ORDER BY lastUpdated DESC
    LIMIT :limit
    OFFSET :offset
    """,
    )
    fun mostRecentUpdated(
        limit: Int,
        offset: Int = 0,
    ): Flow<List<SubjectCollectionEntity>>

    @Query(
        """
        select * from subject_collection 
        where (collectionType is NOT NULL AND collectionType != 'NOT_COLLECTED' AND (:collectionType IS NULL OR collectionType = :collectionType))
        AND (:includeNsfw OR NOT nsfw)
        order by lastUpdated DESC, subjectId DESC
        """,
    )
    @Transaction
    fun filterByCollectionTypePaging(
        collectionType: UnifiedCollectionType? = null,
        includeNsfw: Boolean,
    ): PagingSource<Int, SubjectCollectionAndEpisodes>

    @Query("""SELECT * FROM subject_collection WHERE subjectId = :subjectId""")
    fun findById(subjectId: Int): Flow<SubjectCollectionEntity?>

    @Query("""SELECT * FROM subject_collection WHERE subjectId IN (:subjectIds)""")
    fun filterByIds(subjectIds: IntArray): Flow<List<SubjectCollectionEntity>>

    @Query("SELECT * FROM subject_collection")
    suspend fun listAll(): List<SubjectCollectionEntity>

    @Query(
        """
        SELECT sc.subjectId FROM subject_collection sc WHERE NOT EXISTS (
            SELECT ec.lastFetched FROM episode_collection ec 
            WHERE (ec.subjectId = sc.subjectId) 
                AND (CAST(unixepoch('now', 'subsecond') * 1000 AS int) - ec.lastFetched > :cacheExpiry)
        )
        AND sc.collectionType != 'NOT_COLLECTED'
        """,
    )
    fun subjectIdsWithValidEpisodeCollection(cacheExpiry: Long = 1.hours.inWholeMilliseconds): Flow<List<Int>>

    @Query(
        """
        SELECT lastFetched FROM subject_collection 
        WHERE collectionType != 'NOT_COLLECTED' AND ((:type IS NULL) OR (collectionType = :type))
        ORDER BY lastFetched DESC LIMIT 1
        """,
    )
    suspend fun lastFetched(type: UnifiedCollectionType?): Long

    @Query(
        """
    UPDATE subject_collection 
    SET 
        self_rating_score = COALESCE(:score, self_rating_score), 
        self_rating_comment = COALESCE(:comment, self_rating_comment), 
        self_rating_tags = COALESCE(:tags, self_rating_tags), 
        self_rating_isPrivate = COALESCE(:private, self_rating_isPrivate)
    WHERE subjectId = :subjectId
""",
    )
    suspend fun updateRating(subjectId: Int, score: Int?, comment: String?, tags: List<String>?, private: Boolean?)

    @Query("""SELECT COUNT(*) FROM subject_collection WHERE (collectionType is NOT NULL AND collectionType != 'NOT_COLLECTED' AND (:collectionType IS NULL OR collectionType = :collectionType))""")
    fun countCollected(collectionType: UnifiedCollectionType?): Flow<Int>

    @Query("""UPDATE subject_collection SET cachedStaffUpdated = :time, cachedCharactersUpdated = :time WHERE subjectId = :subjectId""")
    suspend fun updateCachedRelationsUpdated(subjectId: Int, time: Long = currentTimeMillis())

    @Query(
        """
        SELECT sc.subjectId FROM subject_collection sc
        WHERE collectionType IS NOT NULL
        AND collectionType != 'NOT_COLLECTED'
        AND (collectionType IN (:collectionTypes))
        """,
    )
    fun subjectIdsByCollectionType(collectionTypes: List<UnifiedCollectionType>): Flow<List<Int>>

    @Query(
        """
        SELECT sc.nameCn FROM subject_collection sc
        WHERE collectionType IS NOT NULL
        AND collectionType != 'NOT_COLLECTED'
        AND (collectionType IN (:collectionTypes))
        """,
    )
    fun subjectNamesCnByCollectionType(collectionTypes: List<UnifiedCollectionType>): Flow<List<String>>
}

suspend inline fun SubjectCollectionDao.deleteAll(type: UnifiedCollectionType?) {
    if (type == null) {
        deleteAll()
    } else {
        deleteAll(type)
    }
}

data class SubjectCollectionAndEpisodes(
    @Embedded
    val collection: SubjectCollectionEntity,
    @Relation(
        entity = EpisodeCollectionEntity::class,
        parentColumn = "subjectId",
        entityColumn = "subjectId",
    )
    val episodesOfAnyType: List<EpisodeCollectionEntity>,
) {
    override fun toString(): String {
        return "SubjectCollectionAndEpisodes(collection.nameCn=${collection.nameCn}, episodes.size=${episodesOfAnyType.size})"
    }
}

fun SubjectCollectionDao.filterMostRecentUpdated(
    collectionTypes: List<UnifiedCollectionType>?,
    limit: Int,
    offset: Int = 0,
): Flow<List<SubjectCollectionEntity>> = if (collectionTypes == null) {
    mostRecentUpdated(limit, offset)
} else {
    filterMostRecentUpdated(collectionTypes, limit, offset)
}

fun SubjectCollectionDao.filterMostRecentUpdated(
    collectionType: UnifiedCollectionType? = null,
    limit: Int,
): Flow<List<SubjectCollectionEntity>> = filterMostRecentUpdated(listOfNotNull(collectionType), limit)
