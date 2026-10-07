package com.wynime.app.domain.comment

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.network.WynimeEpisodeCommentService
import com.wynime.app.data.network.WynimePersonCommentService
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryUnknownException
import com.wynime.app.domain.usecase.UseCase
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import kotlin.coroutines.CoroutineContext

interface PostCommentUseCase : UseCase {
    suspend operator fun invoke(context: CommentContext, content: String): CommentSendResult
}

class PostCommentUseCaseImpl(
    private val commentService: WynimeEpisodeCommentService,
    private val personCommentService: WynimePersonCommentService,
    private val context: CoroutineContext = Dispatchers.Main,
) : PostCommentUseCase {
    private val logger = logger<PostCommentUseCase>()

    override suspend operator fun invoke(context: CommentContext, content: String): CommentSendResult {
        try {
            withContext(this.context) {
                when (context) {
                    is CommentContext.Episode, is CommentContext.EpisodeReply ->
                        commentService.postEpisodeComment(context, content)

                    is CommentContext.PersonComment ->
                        personCommentService.createComment(context.target, content)

                    is CommentContext.PersonCommentReply ->
                        personCommentService.createReply(context.target, context.commentId, content)

                    is CommentContext.SubjectReview -> error("SubjectReview is not posted through PostCommentUseCase")
                }
            }
            return CommentSendResult.Ok
        } catch (e: Exception) {
            val delegateEx = RepositoryException.wrapOrThrowCancellation(e)

            logger.error(delegateEx) { "Failed to post comment, see exception" }
            return if (delegateEx is RepositoryUnknownException) {
                CommentSendResult.UnknownError(e.toString())
            } else {
                CommentSendResult.NetworkError
            }
        }
    }
}

@Immutable
sealed interface CommentSendResult {
    sealed class Error : CommentSendResult

    data object NetworkError : Error()

    class UnknownError(val message: String) : Error()

    data object Ok : CommentSendResult
}

private suspend fun WynimeEpisodeCommentService.postEpisodeComment(
    context: CommentContext,
    content: String,
) {
    when (context) {
        is CommentContext.Episode ->
            createEpisodeComment(context.episodeId, content)

        is CommentContext.EpisodeReply ->
            createEpisodeReply(context.episodeId, context.commentId, content)

        else -> error("unreachable on postEpisodeComment: $context")
    }
}
