package com.wynime.app.ui.foundation.lists

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
data class PaginatedGroup<T>(
    val title: String,
    val items: List<T>,
    val startIndex: Int,
    val groupIndex: Int,
)

@Stable
class PaginatedListState<T>(

    val groups: List<PaginatedGroup<T>>,
    val listState: LazyListState = LazyListState(),
    private val coroutineScope: CoroutineScope,
) {

    var currentGroupIndex by mutableIntStateOf(0)

    fun navigateToGroup(groupIndex: Int) {
        if (groupIndex in 0 until totalGroupsCount) {
            currentGroupIndex = groupIndex

            coroutineScope.launch {
                val targetIndex = groupStartIndices.getOrNull(groupIndex) ?: 0
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    fun navigateToPreviousGroup() {
        if (canNavigateToPreviousGroup) {
            navigateToGroup(currentGroupIndex - 1)
        }
    }

    fun navigateToNextGroup() {
        if (canNavigateToNextGroup) {
            navigateToGroup(currentGroupIndex + 1)
        }
    }

    val groupStartIndices: List<Int> by derivedStateOf {
        var index = 0
        groups.map { group ->
            val startIndex = index
            index++
            index += group.items.size
            startIndex
        }
    }

    val totalGroupsCount: Int get() = groups.size

    val currentGroup: PaginatedGroup<T>?
        get() = groups.getOrNull(currentGroupIndex)

    val canNavigateToPreviousGroup: Boolean
        get() = currentGroupIndex > 0

    val canNavigateToNextGroup: Boolean
        get() = currentGroupIndex < totalGroupsCount - 1

    fun calculateItemPosition(itemIndexInEpisodes: Int): Int? {
        val groupIndex = findGroupIndexByItem(itemIndexInEpisodes) ?: return null
        val group = groups[groupIndex]
        val posInGroup = itemIndexInEpisodes - group.startIndex

        val headerListIndex = groupStartIndices.getOrNull(groupIndex) ?: return null

        return headerListIndex + 1 + posInGroup
    }

    suspend fun bringIntoView(itemIndex: Int, animate: Boolean = true) {
        val groupIndex = findGroupIndexByItem(itemIndex) ?: return
        currentGroupIndex = groupIndex

        val itemPosition = calculateItemPosition(itemIndex)
        if (itemPosition != null) {
            if (animate) {
                listState.animateScrollToItem(itemPosition)
            } else {
                listState.scrollToItem(itemPosition)
            }
        }
    }

    private fun findGroupIndexByItem(itemIndex: Int): Int? {
        if (groups.isEmpty()) return null

        val last = groups.last()
        val totalItems = last.startIndex + last.items.size
        if (itemIndex < 0 || itemIndex >= totalItems) return null

        val result = groups.binarySearch { it.startIndex.compareTo(itemIndex) }
        return if (result >= 0) {
            var idx = result
            while (idx < groups.lastIndex && groups[idx + 1].startIndex <= itemIndex) {
                idx++
            }
            idx
        } else {
            val insertionPoint = -result - 1
            if (insertionPoint > 0) insertionPoint - 1 else null
        }
    }

}

@Composable
fun <T> rememberPaginatedListState(
    groups: List<PaginatedGroup<T>>,
    key: Any? = null,
    listState: LazyListState = rememberLazyListState(),
): PaginatedListState<T> {
    val coroutineScope = rememberCoroutineScope()
    return remember(key) {
        PaginatedListState(groups, listState, coroutineScope)
    }
}