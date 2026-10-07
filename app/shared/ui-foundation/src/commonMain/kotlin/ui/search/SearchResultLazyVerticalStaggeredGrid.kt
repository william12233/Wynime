package com.wynime.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.wynime.app.domain.foundation.LoadError

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T : Any> SearchResultLazyVerticalGrid(
    items: LazyPagingItems<T>,
    error: @Composable (error: LoadError?) -> Unit,
    modifier: Modifier = Modifier,
    cells: GridCells = GridCells.Adaptive(360.dp),
    state: LazyGridState = rememberLazyGridState(),
    listItemColors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showLoadingIndicatorInFirstPage: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    Box(modifier) {
        Column(Modifier.zIndex(1f)) {
            if (items.loadState.hasError) {
                Box(
                    Modifier
                        .sizeIn(
                            minHeight = Dp.Hairline,
                            minWidth = Dp.Hairline,
                        )
                        .padding(vertical = 8.dp),
                ) {
                    val value = items.rememberLoadErrorState().value
                    error(value)
                }
            }

            LazyVerticalGrid(
                cells,
                Modifier.fillMaxWidth(),
                state,
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = verticalArrangement,
                contentPadding = contentPadding,
            ) {
                content()

                if (items.loadState.refresh is LoadState.Loading) {
                    if (showLoadingIndicatorInFirstPage || !items.isLoadingFirstPage) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ListItem(
                                headlineContent = {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        LoadingIndicator()
                                    }
                                },
                                colors = listItemColors,
                            )
                        }
                    }
                }

                if (items.loadState.append is LoadState.Loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ListItem(
                            headlineContent = {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    LoadingIndicator()
                                }
                            },
                            colors = listItemColors,
                        )
                    }
                }
            }
        }
    }
}

@Stable
object SearchDefaults {

    @Composable
    fun IconTextButton(
        onClick: () -> Unit,
        leadingIcon: @Composable (Modifier) -> Unit,
        modifier: Modifier = Modifier,
        text: @Composable () -> Unit,
    ) {
        TextButton(
            onClick,
            modifier,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            leadingIcon(Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            text()
        }
    }
}
