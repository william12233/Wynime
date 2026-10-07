package com.wynime.app.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.layout.paneHorizontalPadding
import com.wynime.app.ui.foundation.text.ProvideContentColor
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor

@Composable
fun NavTitleHeader(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    trailingActions: @Composable () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    Row(modifier.fillMaxWidth().padding(contentPadding)) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyleContentColor(
                MaterialTheme.typography.headlineSmall,
                MaterialTheme.colorScheme.onSurface,
            ) {
                title()
            }
        }

        Row {
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                navigationIcon()
            }
        }

        Spacer(Modifier.weight(1f))

        Row {
            ProvideContentColor(MaterialTheme.colorScheme.onSurface) {
                trailingActions()
            }
        }
    }
}
