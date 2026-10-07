package com.wynime.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.wynime.app.data.persistent.database.dao.SearchHistoryDao
import com.wynime.app.data.persistent.database.dao.SearchHistoryEntity
import com.wynime.app.data.persistent.database.dao.SearchTagDao
import com.wynime.app.data.repository.Repository
import org.koin.core.component.KoinComponent

class SubjectSearchHistoryRepository(
    private val searchHistory: SearchHistoryDao,
    private val searchTag: SearchTagDao,
) : Repository(), KoinComponent {
    suspend fun addHistory(content: String) = withContext(defaultDispatcher) {
        val normalizedContent = content.trim()
        if (normalizedContent.isEmpty()) {
            return@withContext
        }

        searchHistory.insert(SearchHistoryEntity(content = normalizedContent))
    }

    suspend fun removeHistory(content: String) = withContext(defaultDispatcher) {
        searchHistory.deleteByContent(content)
    }

    fun getHistoryPager(): Flow<PagingData<String>> {
        return Pager(
            config = defaultPagingConfig,
            pagingSourceFactory = { searchHistory.allPager() },
        ).flow.flowOn(defaultDispatcher)
    }

}
