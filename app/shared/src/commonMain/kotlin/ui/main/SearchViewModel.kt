package com.wynime.app.ui.main

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.subject.SubjectSearchCompletionRepository
import com.wynime.app.data.repository.subject.SubjectSearchHistoryRepository
import com.wynime.app.data.repository.subject.SubjectSearchRepository
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.episode.GetAnimeSeasonIdsFlowUseCase
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCase
import com.wynime.app.domain.search.SubjectSearchQuery
import com.wynime.app.domain.search.withYearFilter
import com.wynime.app.ui.exploration.search.SearchPageEffect
import com.wynime.app.ui.exploration.search.SearchPageIntent
import com.wynime.app.ui.exploration.search.SearchPageState
import com.wynime.app.ui.exploration.search.SubjectPreviewItemInfo
import com.wynime.app.ui.exploration.search.buildSearchFilterState
import com.wynime.app.ui.exploration.search.withQuery
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.foundation.launchInBackground
import com.wynime.app.ui.search.PagingSearchState
import com.wynime.app.ui.subject.details.state.SubjectDetailsStateFactory
import com.wynime.app.ui.subject.details.state.SubjectDetailsStateLoader
import com.wynime.app.ui.user.SelfInfoStateProducer
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.SearchStart
import com.wynime.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import com.wynime.utils.analytics.recordEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
open class SearchViewModel(
    initialSearchQuery: SubjectSearchQuery,
) : AbstractViewModel(), KoinComponent {
    private val searchHistoryRepository: SubjectSearchHistoryRepository by inject()
    private val subjectSearchCompletionRepository: SubjectSearchCompletionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectSearchRepository: SubjectSearchRepository by inject()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val getAnimeSeasonIdsFlowUseCase: GetAnimeSeasonIdsFlowUseCase by inject()
    val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase by inject()

    private val initialQuery = initialSearchQuery.normalized()
    private val hasInitialSearchQuery = initialQuery.shouldTriggerSearch()
    private val queryFlow = MutableStateFlow(initialQuery)

    private val nsfwSettingFlow = settingsRepository.uiSettings.flow
        .map { it.searchSettings.nsfwMode }
        .stateIn(backgroundScope, SharingStarted.Lazily, NsfwMode.HIDE)

    private val searchHistoryPager = searchHistoryRepository.getHistoryPager().cachedIn(backgroundScope)
    private val searchState = PagingSearchState(
        createPager = { scope ->
            val rawQuery = queryFlow.value.normalized()
            val explicitR18 = rawQuery.tags?.contains("R18") == true
            val query = rawQuery.copy(
                nsfw = when {
                    explicitR18 -> true
                    nsfwSettingFlow.value == NsfwMode.HIDE -> false
                    else -> null
                },
            )

            subjectSearchRepository.searchSubjects(
                searchQuery = query,
                ignoreDoneAndDropped = {
                    settingsRepository.uiSettings.flow.map {
                        it.searchSettings.ignoreDoneAndDroppedSubjects
                    }.first()
                },
            ).combine(nsfwSettingFlow) { data, nsfwMode ->
                data.map { subject ->
                    SubjectPreviewItemInfo.compute(
                        subject.subjectInfo,
                        subject.mainEpisodeCount,
                        nsfwModeSettings = if (explicitR18) {
                            NsfwMode.DISPLAY
                        } else {
                            nsfwMode
                        },
                        relatedPersonList = subject.lightSubjectRelations.lightRelatedPersonInfoList,
                        characters = subject.lightSubjectRelations.lightRelatedCharacterInfoList,
                    )
                }
            }.cachedIn(scope)
        },
        backgroundScope = backgroundScope,
    )

    private val _searchPageState = MutableStateFlow(
        SearchPageState(
            query = initialQuery,
            hasActiveSearch = false,
            removingHistory = null,
            searchFilterState = buildSearchFilterState(initialQuery.tags.orEmpty()),
            selectedItemIndex = -1,
            searchHistoryPager = searchHistoryPager,
            searchState = searchState,
        ),
    )
    val searchPageState = _searchPageState.asStateFlow()

    private val _searchPageEffects = MutableSharedFlow<SearchPageEffect>(extraBufferCapacity = 1)
    val searchPageEffects = _searchPageEffects.asSharedFlow()

    val selfInfoFlow = SelfInfoStateProducer(koin = getKoin()).flow
    val subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)

    private var currentPreviewingSubject: SubjectInfo? = null
    private var initialSearchQueryStarted = false

    init {

        launchInBackground {
            try {
                val seasons = getAnimeSeasonIdsFlowUseCase().first()
                updateSearchPageState { it.copy(seasons = seasons) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {

            }
        }
    }

    fun suggestionsPager(query: String): Flow<PagingData<String>> {
        return subjectSearchCompletionRepository.completionsFlow(query.trim())
    }

    fun onSearchPageIntent(intent: SearchPageIntent) {
        when (intent) {
            SearchPageIntent.ClearSelection -> {
                updateSearchPageState {
                    if (it.selectedItemIndex == -1) {
                        it
                    } else {
                        it.copy(selectedItemIndex = -1)
                    }
                }
            }

            is SearchPageIntent.ChangeSort -> {
                refreshSearch(_searchPageState.value.query.copy(sort = intent.sort))
            }

            is SearchPageIntent.ChangeYear -> {

                applyQueryAndRefresh(_searchPageState.value.query.withYearFilter(intent.year))
            }

            is SearchPageIntent.ChangeSeason -> {
                val query = _searchPageState.value.query

                if (intent.season == null || query.year != null) {
                    applyQueryAndRefresh(query.copy(season = intent.season))
                }
            }

            is SearchPageIntent.Play -> {
                launchInBackground {
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(intent.item.subjectId)
                        .first()
                        .firstOrNull()
                        ?.let {
                            _searchPageEffects.emit(
                                SearchPageEffect.NavigateToEpisodeDetails(
                                    subjectId = intent.item.subjectId,
                                    episodeId = it.episodeInfo.episodeId,
                                ),
                            )
                        }
                }
            }

            is SearchPageIntent.RemoveHistory -> {
                updateSearchPageState { it.copy(removingHistory = intent.text) }
                launchInBackground {
                    searchHistoryRepository.removeHistory(intent.text)
                    updateSearchPageState { state ->
                        if (state.removingHistory == intent.text) {
                            state.copy(removingHistory = null)
                        } else {
                            state
                        }
                    }
                }
            }

            is SearchPageIntent.SelectResult -> {
                updateSearchPageState { state ->
                    state.copy(
                        selectedItemIndex = intent.index,
                    )
                }
                Analytics.recordEvent(SubjectEnter) {
                    put("source", "search")
                    put("subject_id", intent.item.subjectId)
                    put("position", intent.index)
                }
                viewSubjectDetails(intent.item)
            }

            is SearchPageIntent.OpenSubjectDetails -> {
                Analytics.recordEvent(SubjectEnter) {
                    put("source", "search")
                    put("subject_id", intent.item.subjectId)
                    put("position", intent.index)
                }
                launchInBackground {
                    _searchPageEffects.emit(
                        SearchPageEffect.NavigateToSubjectDetails(
                            subjectId = intent.item.subjectId,
                            title = intent.item.title,
                            originalTitle = intent.item.originalTitle,
                            imageUrl = intent.item.imageUrl,
                        ),
                    )
                }
            }

            SearchPageIntent.StartInitialSearch -> {
                if (!initialSearchQueryStarted) {
                    initialSearchQueryStarted = true
                    if (hasInitialSearchQuery) {
                        refreshSearch(_searchPageState.value.query)
                    }
                }
            }

            is SearchPageIntent.UpdateQuery -> {
                updateQuery(intent.query, intent.submit)
            }
        }
    }

    fun reloadCurrentSubjectDetails() {
        val curr = currentPreviewingSubject ?: return
        subjectDetailsStateLoader.load(curr.subjectId, curr, force = true)
    }

    private fun updateQuery(query: SubjectSearchQuery, submit: Boolean) {
        val updatedQuery = query.normalized()
        updateQueryState(updatedQuery)
        if (updatedQuery.shouldTriggerSearch()) {
            if (submit) {
                Analytics.recordEvent(SearchStart) {
                    put("query", updatedQuery.keywords)
                    put("query_length", updatedQuery.keywords.length)
                    put("has_query", updatedQuery.keywords.isNotEmpty())
                    put("tags", updatedQuery.tags.orEmpty().joinToString(","))
                    put("tag_count", updatedQuery.tags.orEmpty().size)
                }
            }
            refreshSearch(updatedQuery)
            if (submit && updatedQuery.keywords.isNotEmpty()) {
                launchInBackground {
                    searchHistoryRepository.addHistory(updatedQuery.keywords)
                }
            }
        }
    }

    private fun applyQueryAndRefresh(query: SubjectSearchQuery) {
        if (query.shouldTriggerSearch()) {
            refreshSearch(query)
        } else {

            updateQueryState(query)
            clearSearchResults()
            updateSearchPageState { it.copy(hasActiveSearch = false) }
        }
    }

    private fun refreshSearch(query: SubjectSearchQuery) {
        val normalizedQuery = query.normalized()
        updateQueryState(normalizedQuery)

        if (normalizedQuery.shouldTriggerSearch()) {
            clearSubjectDetails()
            searchState.startSearch()
            updateSearchPageState {
                it.copy(
                    hasActiveSearch = true,
                    selectedItemIndex = -1,
                )
            }
        }
    }

    private fun clearSearchResults() {
        clearSubjectDetails()
        searchState.clear()
    }

    private fun clearSubjectDetails() {
        currentPreviewingSubject = null
        subjectDetailsStateLoader.clear()
    }

    private fun viewSubjectDetails(previewItem: SubjectPreviewItemInfo) {

        subjectDetailsStateLoader.load(
            previewItem.subjectId,
            placeholder = SubjectInfo.createPlaceholder(
                previewItem.subjectId,
                previewItem.originalTitle,
                previewItem.imageUrl,
                previewItem.title,
            ).also { currentPreviewingSubject = it },
        )
    }

    private fun updateQueryState(query: SubjectSearchQuery) {
        val normalizedQuery = query.normalized()
        if (queryFlow.value == normalizedQuery && _searchPageState.value.query == normalizedQuery) {
            return
        }

        queryFlow.value = normalizedQuery
        updateSearchPageState { it.withQuery(normalizedQuery) }
    }

    private inline fun updateSearchPageState(block: (SearchPageState) -> SearchPageState) {
        _searchPageState.update(block)
    }
}

private fun SubjectSearchQuery.shouldTriggerSearch(): Boolean {
    return keywords.isNotEmpty() ||
            !tags.isNullOrEmpty() ||
            year != null ||
            rating != null ||
            nsfw != null
}
