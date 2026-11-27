package com.wynime.app.ui.subject.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_mediasource_retry
import com.wynime.app.ui.lang.subject_episode_catalog_unavailable
import org.jetbrains.compose.resources.stringResource

data class EpisodeCatalogStatus(val loading: Boolean = false, val hasError: Boolean = false)

@Composable
internal fun EpisodeCatalogStatusRow(status: EpisodeCatalogStatus, onRetry: () -> Unit) {
    when {
        status.hasError -> RetryEpisodeMetadata(
            stringResource(Lang.subject_episode_catalog_unavailable),
            onRetry,
        )
        status.loading -> Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
internal fun RetryEpisodeMetadata(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) {
            Text(stringResource(Lang.settings_mediasource_retry))
        }
    }
}
