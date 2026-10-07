package com.wynime.app.ui.subject.details.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wynime.app.ui.foundation.layout.desktopTitleBar
import com.wynime.app.ui.foundation.layout.desktopTitleBarPadding
import com.wynime.app.ui.foundation.layout.plus

@Composable
internal fun <T : Any> ViewAllSheet(
    title: String,
    items: LazyPagingItems<T>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    cellMinWidth: Dp = 240.dp,
    itemContent: @Composable (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest,
        modifier = modifier.desktopTitleBarPadding().statusBarsPadding(),
        contentWindowInsets = {
            BottomSheetDefaults.windowInsets
                .add(WindowInsets.desktopTitleBar())
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                GridCells.Adaptive(minSize = cellMinWidth),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
                    .plus(PaddingValues(bottom = 16.dp)),
            ) {
                items(
                    items.itemCount,
                    items.itemKey(),
                    contentType = items.itemContentType(),
                ) { index ->
                    items[index]?.let { itemContent(it) }
                }
            }
        }
    }
}
