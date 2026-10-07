package com.wynime.app.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_bangumi_sync_continue_background
import com.wynime.app.ui.lang.foundation_bangumi_sync_description
import com.wynime.app.ui.lang.foundation_bangumi_sync_failed
import com.wynime.app.ui.lang.foundation_bangumi_sync_fetching_episodes
import com.wynime.app.ui.lang.foundation_bangumi_sync_fetching_metadata
import com.wynime.app.ui.lang.foundation_bangumi_sync_fetching_subjects
import com.wynime.app.ui.lang.foundation_bangumi_sync_finishing
import com.wynime.app.ui.lang.foundation_bangumi_sync_in_progress
import com.wynime.app.ui.lang.foundation_bangumi_sync_inserting
import com.wynime.app.ui.lang.foundation_bangumi_sync_preparing
import com.wynime.app.ui.lang.foundation_bangumi_sync_success
import com.wynime.app.ui.lang.foundation_bangumi_sync_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun BangumiFullSyncStateDialog(
    state: BangumiSyncState?,
    onDismissRequest: () -> Unit,
) {
    val canDismiss = state?.finished == true
    val progress = state?.progressFraction()
    AlertDialog(
        title = { Text(stringResource(Lang.foundation_bangumi_sync_title)) },
        text = {
            Column {
                Text(renderBangumiSyncState(state))
                Spacer(modifier = Modifier.height(24.dp))
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator({ progress }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(Lang.foundation_bangumi_sync_description))
            }
        },
        onDismissRequest = { if (canDismiss) onDismissRequest() },
        confirmButton = {
            if (canDismiss) {
                TextButton(onDismissRequest) {
                    Text(stringResource(Lang.foundation_bangumi_sync_continue_background))
                }
            } else {
                TextButton({}, enabled = false) {
                    Text(stringResource(Lang.foundation_bangumi_sync_in_progress))
                }
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = canDismiss,
            dismissOnBackPress = canDismiss,
        ),
    )
}

@Composable
private fun renderBangumiSyncState(state: BangumiSyncState?): String {
    return when (state) {
        null -> stringResource(Lang.foundation_bangumi_sync_preparing)
        BangumiSyncState.Preparing -> stringResource(Lang.foundation_bangumi_sync_fetching_metadata)
        is BangumiSyncState.FetchingSubjects -> formatCount(
            stringResource(Lang.foundation_bangumi_sync_fetching_subjects, state.fetchedCount),
            state.fetchedCount,
            state.totalCount,
        )

        is BangumiSyncState.FetchingEpisodes -> formatCount(
            stringResource(Lang.foundation_bangumi_sync_fetching_episodes, state.fetchedCount),
            state.fetchedCount,
            state.totalCount,
        )

        is BangumiSyncState.Inserting -> formatCount(
            stringResource(Lang.foundation_bangumi_sync_inserting, state.savedCount),
            state.savedCount,
            state.totalCount,
        )

        is BangumiSyncState.Finishing -> formatCount(
            stringResource(Lang.foundation_bangumi_sync_finishing, state.savedCount),
            state.savedCount,
            state.totalCount,
        )
        is BangumiSyncState.Finished -> {
            if (state.error != null || state.localError != null) {
                stringResource(
                    Lang.foundation_bangumi_sync_failed,
                    state.savedCount,
                    state.localError ?: state.error.toString(),
                )
            } else {
                stringResource(Lang.foundation_bangumi_sync_success, state.savedCount)
            }
        }

        BangumiSyncState.Unsupported -> stringResource(Lang.foundation_bangumi_sync_in_progress)
    }
}

private fun formatCount(text: String, current: Int, total: Int?): String {
    return if (total == null) text else "$text · $current / $total"
}

private fun BangumiSyncState.progressFraction(): Float? {
    val (current, total) = when (this) {
        is BangumiSyncState.FetchingSubjects -> fetchedCount to totalCount
        is BangumiSyncState.FetchingEpisodes -> fetchedCount to totalCount
        is BangumiSyncState.Inserting -> savedCount to totalCount
        is BangumiSyncState.Finishing -> savedCount to totalCount
        is BangumiSyncState.Finished -> 1 to 1
        else -> return null
    }
    return total?.takeIf { it > 0 }?.let { (current.toFloat() / it).coerceIn(0f, 1f) }
        ?: if (this is BangumiSyncState.Finished) 1f else null
}

@Composable
@Preview
private fun PreviewBangumiFullSyncDialogSaved() {
    ProvideCompositionLocalsForPreview {
        BangumiFullSyncStateDialog(
            state = BangumiSyncState.Inserting(123),
            onDismissRequest = {},
        )
    }
}

@Composable
@Preview
private fun PreviewBangumiFullSyncDialogSyncTimeline() {
    ProvideCompositionLocalsForPreview {
        BangumiFullSyncStateDialog(
            state = BangumiSyncState.Finishing(100),
            onDismissRequest = {},
        )
    }
}
