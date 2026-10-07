package com.wynime.app.ui.download.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.download.components.DownloadItem
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_management_downloading_count
import com.wynime.app.ui.lang.cache_management_finished_count
import com.wynime.app.ui.lang.cache_management_selection_downloading_count
import com.wynime.app.ui.lang.cache_management_selection_summary
import com.wynime.app.ui.lang.cache_subject_pause_all
import com.wynime.app.ui.lang.cache_subject_resume_all
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import org.jetbrains.compose.resources.stringResource

object SubjectDownloadsTestTags {
    const val SUMMARY_ROW = "subject_downloads_summary"
    const val PAUSE_ALL = "subject_downloads_pause_all"
    const val RESUME_ALL = "subject_downloads_resume_all"
    const val LOADING = "subject_downloads_loading"
}

@Composable
fun SubjectDownloadsSummaryRow(
    downloads: List<DownloadItem>,
    totalEpisodeCount: Int?,
    inSelection: Boolean,
    selectedEntries: List<DownloadItem>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(SubjectDownloadsTestTags.SUMMARY_ROW)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val summaryText = if (inSelection) {
            selectionSummaryText(selectedEntries)
        } else {
            downloadSummaryText(downloads, totalEpisodeCount)
        }
        Text(
            summaryText,
            Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!inSelection) {
            PauseOrResumeAllTextButton(downloads, onPauseAll, onResumeAll)
        }
    }
}

@Composable
private fun PauseOrResumeAllTextButton(
    downloads: List<DownloadItem>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anyDownloading = downloads.any {
        !it.isFinished && !it.isPaused && !it.isFailed
    }
    val anyPaused = downloads.any { it.isPaused }
    when {
        anyDownloading -> {
            TextButton(onClick = onPauseAll, enabled = downloads.none { it.isBusy }, modifier = modifier.testTag(SubjectDownloadsTestTags.PAUSE_ALL)) {
                Text(stringResource(Lang.cache_subject_pause_all))
            }
        }

        anyPaused -> {
            TextButton(onClick = onResumeAll, enabled = downloads.none { it.isBusy }, modifier = modifier.testTag(SubjectDownloadsTestTags.RESUME_ALL)) {
                Text(stringResource(Lang.cache_subject_resume_all))
            }
        }
    }
}

@Composable
private fun downloadSummaryText(
    downloads: List<DownloadItem>,
    totalEpisodeCount: Int?,
): String {
    val finishedCount = downloads.filter { it.isFinished }.map { it.episodeId }.distinct().size
    val downloadedEpisodeCount = downloads.map { it.episodeId }.distinct().size
    val totalCount = totalEpisodeCount?.coerceAtLeast(downloadedEpisodeCount) ?: downloadedEpisodeCount

    val downloadingCount = downloads.count { !it.isFinished && !it.isFailed }

    val parts = buildList {
        add(stringResource(Lang.cache_management_finished_count, finishedCount, totalCount))
        totalDownloadSize(downloads)?.let { add("$it") }
        if (downloadingCount > 0) {
            add(stringResource(Lang.cache_management_downloading_count, downloadingCount))
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun selectionSummaryText(selectedEntries: List<DownloadItem>): String {
    val totalSize = totalDownloadSize(selectedEntries) ?: 0.bytes
    val downloadingCount = selectedEntries.count { !it.isFinished && !it.isPaused && !it.isFailed }
    val summary = stringResource(Lang.cache_management_selection_summary, selectedEntries.size, "$totalSize")
    return if (downloadingCount > 0) {
        "$summary · ${stringResource(Lang.cache_management_selection_downloading_count, downloadingCount)}"
    } else {
        summary
    }
}

private fun totalDownloadSize(episodes: List<DownloadItem>): FileSize? {
    var sum = 0L
    var any = false
    episodes.forEach { episode ->
        if (episode.totalSize != FileSize.Unspecified) {
            sum += episode.totalSize.inBytes
            any = true
        }
    }
    return if (any) sum.bytes else null
}

@Composable
fun SubjectDownloadsHeader(
    title: String?,
    downloads: List<DownloadItem>,
    totalEpisodeCount: Int?,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Text(
                title.orEmpty(),
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                downloadSummaryText(downloads, totalEpisodeCount),
                Modifier.padding(start = 12.dp).weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PauseOrResumeAllTextButton(downloads, onPauseAll, onResumeAll)
    }
}
