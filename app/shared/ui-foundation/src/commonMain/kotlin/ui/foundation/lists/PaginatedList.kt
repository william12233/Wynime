package com.wynime.app.ui.foundation.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_paginated_list_next_group
import com.wynime.app.ui.lang.foundation_paginated_list_previous_group
import com.wynime.app.ui.lang.foundation_paginated_list_select_group
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T> PaginatedList(
    state: PaginatedListState<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    headerContent: @Composable (group: PaginatedGroup<T>) -> Unit = { DefaultGroupHeader(title = it.title) },
    onItemClick: ((T) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {

    Column(modifier = modifier) {

        PaginatedListNavigation(
            state = state,
            modifier = Modifier.fillMaxWidth(),
        )

        LazyColumn(
            state = state.listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
        ) {
            state.groups.forEach { group ->
                item(key = "PaginatedList_header_${group.groupIndex}") {
                    headerContent(group)
                }

                items(
                    items = group.items,
                    key = { "PaginatedList_${group.groupIndex}_${it.hashCode()}" },
                ) { item ->
                    if (onItemClick != null) {
                        Surface(
                            onClick = { onItemClick(item) },
                            color = Color.Transparent,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            itemContent(item)
                        }
                    } else {
                        itemContent(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> PaginatedListNavigation(
    state: PaginatedListState<T>,
    modifier: Modifier = Modifier,
) {
    var showGroupSelector by rememberSaveable { mutableStateOf(false) }
    val previousGroupText = stringResource(Lang.foundation_paginated_list_previous_group)
    val selectGroupText = stringResource(Lang.foundation_paginated_list_select_group)
    val nextGroupText = stringResource(Lang.foundation_paginated_list_next_group)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        IconButton(
            onClick = { state.navigateToPreviousGroup() },
            enabled = state.canNavigateToPreviousGroup,
        ) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = previousGroupText,
                tint = if (state.canNavigateToPreviousGroup) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = { showGroupSelector = true },
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.currentGroup?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = selectGroupText,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = showGroupSelector,
                onDismissRequest = { showGroupSelector = false },
            ) {
                state.groups.forEachIndexed { index, group ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = group.title,
                                color = if (index == state.currentGroupIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            state.navigateToGroup(index)
                            showGroupSelector = false
                        },
                    )
                }
            }
        }

        IconButton(
            onClick = { state.navigateToNextGroup() },
            enabled = state.canNavigateToNextGroup,
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = nextGroupText,
                tint = if (state.canNavigateToNextGroup) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

@Composable
fun DefaultGroupHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
