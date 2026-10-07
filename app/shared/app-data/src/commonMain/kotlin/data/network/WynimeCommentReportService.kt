package com.wynime.app.data.network

import kotlinx.coroutines.Dispatchers
import com.wynime.app.data.models.comment.CommentReportReason
import com.wynime.app.data.models.comment.CommentReportTargetType
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

open class WynimeCommentReportService(
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {

    open suspend fun createReport(
        targetType: CommentReportTargetType,
        targetId: String,
        reason: CommentReportReason,
        commentAuthorId: String? = null,
        detail: String? = null,
        contentSnapshot: String? = null,
        subjectId: Long? = null,
        episodeId: Long? = null,
    ): Unit {
        throw RepositoryRequestError("目前未提供官方 Bangumi 留言檢舉介面")
    }
}

