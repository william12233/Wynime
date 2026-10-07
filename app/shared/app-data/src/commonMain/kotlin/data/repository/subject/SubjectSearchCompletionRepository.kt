package com.wynime.app.data.repository.subject

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.network.WynimeSubjectSearchService
import com.wynime.app.data.network.SubjectSearchField
import com.wynime.app.data.network.SubjectSearchFilters
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.runWrappingExceptionAsLoadResult
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.logging.error

class SubjectSearchCompletionRepository(
    private val wynimeSubjectSearchService: WynimeSubjectSearchService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    settingsRepository: SettingsRepository,
) : Repository() {
    private val ignoreDoneAndDroppedFlow =
        settingsRepository.uiSettings.flow.map { it.searchSettings.ignoreDoneAndDroppedSubjects }
    private val nsfwSettings = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

    fun completionsFlow(query: String): Flow<PagingData<String>> = Pager(
        config = defaultPagingConfig,
        pagingSourceFactory = {
            object : PagingSource<Int, String>() {
                override fun getRefreshKey(state: PagingState<Int, String>): Int? = null

                override suspend fun load(
                    params: LoadParams<Int>
                ): LoadResult<Int, String> = runWrappingExceptionAsLoadResult<Int, String> {

                    val subjects = wynimeSubjectSearchService.searchSubjects(
                        keyword = query,
                        limit = params.loadSize,
                        filters = SubjectSearchFilters(
                            nsfw = when (nsfwSettings.first()) {
                                NsfwMode.DISPLAY -> null
                                NsfwMode.BLUR -> false
                                NsfwMode.HIDE -> false
                            },
                        ),
                        fields = listOf(SubjectSearchField.NAME),
                    )

                    val filteredSubjects = if (ignoreDoneAndDroppedFlow.first()) {
                        val excludedIds = subjectCollectionRepository.getSubjectIdsByCollectionType(
                            types = listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.DROPPED),
                        ).first()

                        subjects.filter { it.subjectInfo.subjectId !in excludedIds }
                    } else {
                        subjects
                    }

                    LoadResult.Page(
                        data = filteredSubjects
                            .map { it.subjectInfo.nameCn.ifEmpty { it.subjectInfo.name } }
                            .filter { it.isNotBlank() }
                            .distinct(),
                        prevKey = null,
                        nextKey = null,
                    )
                }.also {
                    if (it is LoadResult.Error) {
                        logger.error(it.throwable) { "Failed to get search completions, query: $query" }
                    }
                }
            }
        },
    ).flow.flowOn(defaultDispatcher)
}
