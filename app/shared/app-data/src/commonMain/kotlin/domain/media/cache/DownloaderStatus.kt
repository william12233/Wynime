/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import me.him188.ani.utils.httpdownloader.DownloadError
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.SegmentFailure

/**
 * 下载引擎对一条记录的内部状态, 供详情页诊断下载为何停滞.
 */
sealed interface DownloaderStatus {
    /**
     * HTTP 引擎正在解析视频地址, 下载任务尚未创建.
     */
    data object Resolving : DownloaderStatus

    /**
     * HTTP 引擎.
     */
    data class Http(
        val status: DownloadStatus,
        val error: DownloadError?,
        val downloadedSegments: Int,
        val totalSegments: Int,
        /**
         * 分片重试期间最近一次失败, 分片成功后为 `null`.
         */
        val lastSegmentFailure: SegmentFailure? = null,
    ) : DownloaderStatus
}
