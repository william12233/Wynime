package com.wynime.app.ui.settings.tabs.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_about_feedback
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogTab(
    onClickFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        val listItemColors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_feedback)) },
            modifier = Modifier.clickable(onClick = onClickFeedback),
            leadingContent = {
                Icon(Icons.Outlined.Feedback, contentDescription = null)
            },
            colors = listItemColors,
        )

        PlatformLoggingItems(listItemColors)
    }
}

@Composable
internal expect fun ColumnScope.PlatformLoggingItems(
    listItemColors: ListItemColors,
)

@Composable
@Preview
private fun PreviewLogTab() {
    ProvideCompositionLocalsForPreview {
        Surface {
            LogTab(
                onClickFeedback = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
