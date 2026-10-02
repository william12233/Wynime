/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.details

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.cache.DownloaderStatus
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.ui.download.components.renderFileSize
import me.him188.ani.app.ui.download.components.renderSpeed
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_details_download_state
import me.him188.ani.app.ui.lang.cache_details_error
import me.him188.ani.app.ui.lang.cache_details_http_state_canceled
import me.him188.ani.app.ui.lang.cache_details_http_state_initializing
import me.him188.ani.app.ui.lang.cache_details_http_state_merging
import me.him188.ani.app.ui.lang.cache_details_http_state_resolving
import me.him188.ani.app.ui.lang.cache_details_last_error
import me.him188.ani.app.ui.lang.cache_details_last_error_summary
import me.him188.ani.app.ui.lang.cache_details_segments
import me.him188.ani.app.ui.lang.cache_details_state_completed
import me.him188.ani.app.ui.lang.cache_details_state_downloading
import me.him188.ani.app.ui.lang.cache_details_state_failed
import me.him188.ani.app.ui.lang.cache_details_state_paused
import me.him188.ani.app.ui.lang.cache_details_transfer
import me.him188.ani.app.ui.lang.cache_details_transfer_summary
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.utils.httpdownloader.DownloadStatus
import org.jetbrains.compose.resources.stringResource

/**
 * 下載器區塊：下載狀態、傳輸統計、HTTP 分片進度與錯誤資訊。
 */
internal fun LazyGridScope.downloaderDetailsItems(details: DownloaderDetails, unknownText: String) {
    item {
        ListItem(
            headlineContent = { Text(stringResource(Lang.cache_details_download_state)) },
            leadingContent = { Icon(Icons.Rounded.Downloading, contentDescription = null) },
            supportingContent = { Text(downloadStateText(details), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        )
    }
    item {
        val stats = details.stats
        ListItem(
            headlineContent = { Text(stringResource(Lang.cache_details_transfer)) },
            leadingContent = { Icon(Icons.Rounded.Speed, contentDescription = null) },
            supportingContent = {
                Text(
                    stringResource(
                        Lang.cache_details_transfer_summary,
                        renderFileSize(stats.downloadedBytes).ifEmpty { unknownText },
                        renderFileSize(stats.totalSize).ifEmpty { unknownText },
                        renderSpeed(stats.downloadSpeed.orZero()),
                        renderSpeed(stats.uploadSpeed.orZero()),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
    when (val status = details.status) {
        is DownloaderStatus.Http -> {
            if (status.totalSegments > 0) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(Lang.cache_details_segments, status.downloadedSegments, status.totalSegments))
                        },
                        leadingContent = { Spacer(Modifier.size(24.dp)) },
                    )
                }
            }
            val lastFailure = status.lastSegmentFailure
            if (lastFailure != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(Lang.cache_details_last_error)) },
                        leadingContent = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
                        supportingContent = {
                            SelectionContainer {
                                Text(
                                    stringResource(
                                        Lang.cache_details_last_error_summary,
                                        lastFailure.attempt,
                                        lastFailure.maxAttempts,
                                        lastFailure.message,
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    )
                }
            }
            val error = status.error
            if (error != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(Lang.cache_details_error)) },
                        leadingContent = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
                        supportingContent = {
                            SelectionContainer {
                                Text(error.technicalMessage ?: error.code.name, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        },
                    )
                }
            }
        }

        DownloaderStatus.Resolving, null -> Unit
    }
}

/**
 * 記錄狀態，HTTP 引擎有額外資訊時以 " · " 追加。
 */
@Composable
private fun downloadStateText(details: DownloaderDetails): String {
    val base = stringResource(
        when (details.cacheState) {
            MediaCacheState.IN_PROGRESS -> Lang.cache_details_state_downloading
            MediaCacheState.PAUSED -> Lang.cache_details_state_paused
            MediaCacheState.FAILED -> Lang.cache_details_state_failed
            MediaCacheState.COMPLETED -> Lang.cache_details_state_completed
        },
    )
    val detail = when (val status = details.status) {
        is DownloaderStatus.Http -> when (status.status) {
            DownloadStatus.INITIALIZING -> Lang.cache_details_http_state_initializing
            DownloadStatus.MERGING -> Lang.cache_details_http_state_merging
            DownloadStatus.CANCELED -> Lang.cache_details_http_state_canceled
            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.COMPLETED, DownloadStatus.FAILED -> null
        }

        DownloaderStatus.Resolving -> Lang.cache_details_http_state_resolving
        null -> null
    }
    return if (detail == null) base else "$base · ${stringResource(detail)}"
}

private fun FileSize.orZero(): FileSize = if (isUnspecified) FileSize.Zero else this
