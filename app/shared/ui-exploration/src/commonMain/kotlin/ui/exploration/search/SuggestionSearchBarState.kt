package com.wynime.app.ui.exploration.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.wynime.app.tools.MonoTasker
import com.wynime.app.ui.adaptive.AdaptiveSearchBar
import com.wynime.app.ui.foundation.interaction.onEnterKeyEvent
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.navigation.BackHandler
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.SearchStart
import com.wynime.utils.analytics.recordEvent

@Stable
class SuggestionSearchBarState(
    historyPager: Flow<PagingData<String>>,
    suggestionsPager: Flow<PagingData<String>>,
    private val clearSearch: () -> Unit,
    private val onRemoveHistory: suspend (text: String) -> Unit,
    queryFlow: Flow<String>,
    private val setQueryValue: (String) -> Unit,
    private val onStartSearchRequest: (query: String) -> Unit = {},
    private val tagsProvider: () -> List<String> = { emptyList() },
    backgroundScope: CoroutineScope,
) {

    var editingQuery: String by mutableStateOf("")
        private set

    val historyPager = historyPager.cachedIn(backgroundScope)
    val suggestionsPager = suggestionsPager.cachedIn(backgroundScope)

    data class Presentation(
        val query: String,
        val previewType: SuggestionSearchPreviewType =
            if (query.isEmpty()) SuggestionSearchPreviewType.HISTORY else SuggestionSearchPreviewType.SUGGESTIONS,
        val isPlaceholder: Boolean = false,
    )

    val presentationFlow = queryFlow.map {
        Presentation(it)
    }.stateIn(
        backgroundScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Presentation(
            "",
            isPlaceholder = true,
        ),
    )

    var expanded by mutableStateOf(false)

    fun clear() {
        setQuery("")
        expanded = false
        clearSearch()
    }

    fun setQuery(value: String) {
        editingQuery = value
        setQueryValue(value)
    }

    fun startSearch() {
        val rawQuery = editingQuery
        val query = rawQuery.trim()
        val tags = tagsProvider()
        if (query != rawQuery) {
            setQuery(query)
        }
        Analytics.recordEvent(SearchStart) {
            put("query", query)
            put("query_length", query.length)
            put("has_query", query.isNotEmpty())
            put("tags", tags.joinToString(","))
            put("tag_count", tags.size)
        }
        expanded = false
        onStartSearchRequest(query)
    }

    private val removeHistoryTasker = MonoTasker(backgroundScope)
    var removingHistory: String? by mutableStateOf(null)
        private set

    fun removeHistory(text: String) {
        removingHistory = text
        removeHistoryTasker.launch {
            onRemoveHistory(text)
            withContext(Dispatchers.Main) {
                removingHistory = text
            }
        }
    }
}

@Immutable
enum class SuggestionSearchPreviewType {
    HISTORY,
    SUGGESTIONS
}

@Composable
fun SuggestionSearchBar(
    state: SuggestionSearchBarState,
    modifier: Modifier = Modifier,
    inputFieldModifier: Modifier = Modifier,
    windowInsets: WindowInsets = WynimeWindowInsets.forSearchBar(),
    placeholder: @Composable (() -> Unit)? = null,
) {
    BackHandler(state.expanded) {
        state.expanded = false
    }
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()

    AdaptiveSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = state.editingQuery,
                onQueryChange = { state.setQuery(it.trim('\n')) },
                onSearch = {
                    state.expanded = false
                    state.startSearch()
                },
                expanded = state.expanded,
                onExpandedChange = { state.expanded = it },
                inputFieldModifier.fillMaxWidth().onEnterKeyEvent {
                    state.startSearch()
                    true
                },
                placeholder = placeholder,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon =
                    if (presentation.query.isNotEmpty() || state.expanded) {
                        {
                            IconButton({ state.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    } else null,
            )
        },
        expanded = state.expanded,
        onExpandedChange = { state.expanded = it },
        modifier,
        windowInsets = windowInsets,
    ) {
        val values = when (presentation.previewType) {
            SuggestionSearchPreviewType.HISTORY -> state.historyPager
            SuggestionSearchPreviewType.SUGGESTIONS -> state.suggestionsPager
        }.collectAsLazyPagingItemsWithLifecycle()
        LazyColumn {
            items(
                values.itemCount,
                key = values.itemKey { "search-suggestion-$it" },
                contentType = values.itemContentType { 1 },
            ) { index ->
                val text = values[index] ?: return@items
                ListItem(
                    leadingContent = if (presentation.previewType == SuggestionSearchPreviewType.HISTORY) {
                        { Icon(Icons.Default.History, contentDescription = null) }
                    } else {
                        null
                    },
                    headlineContent = { Text(text) },
                    modifier = Modifier.animateItem().clickable {
                        state.setQuery(text)
                        state.startSearch()
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    trailingContent = if (presentation.previewType == SuggestionSearchPreviewType.HISTORY) {
                        {
                            IconButton({ state.removeHistory(text) }, enabled = state.removingHistory != text) {
                                Icon(Icons.Default.Close, contentDescription = "删除 $text")
                            }
                        }
                    } else null,
                )
            }
        }
    }
}
