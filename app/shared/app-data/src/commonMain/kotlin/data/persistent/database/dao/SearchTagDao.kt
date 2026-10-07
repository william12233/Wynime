package com.wynime.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchTagDao {
    @Upsert
    suspend fun insert(item: SearchTagEntity)

    @Query("select * from `search_tag` order by useCount desc")
    fun getFlow(): Flow<List<SearchTagEntity>>

    @Query("update `search_tag` set `useCount` = `useCount` + 1 where `content`=:content")
    suspend fun increaseCountByName(content: String)

    @Query("delete from `search_tag` where `content`=:content")
    suspend fun deleteByName(content: String)

    @Query("update `search_tag` set `useCount` = `useCount` + 1 where `id`=:id")
    suspend fun increaseCountById(id: Int)

    @Query("delete from `search_tag` where `id`=:id")
    suspend fun deleteById(id: Int)
}

@Entity(tableName = "search_tag")
data class SearchTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,

    val useCount: Int = 1,
)