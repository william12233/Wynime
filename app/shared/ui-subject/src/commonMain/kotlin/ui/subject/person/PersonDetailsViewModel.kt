package com.wynime.app.ui.subject.person

import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.comment.CommentReportTargetType
import com.wynime.app.data.models.person.CharacterDetailsInfo
import com.wynime.app.data.models.person.PersonCommentTarget
import com.wynime.app.data.models.person.PersonDetailsInfo
import com.wynime.app.data.network.WynimeCommentReportService
import com.wynime.app.data.repository.RepositoryServiceUnavailableException
import com.wynime.app.data.repository.person.PersonCommentRepository
import com.wynime.app.data.repository.person.PersonDetailsRepository
import com.wynime.app.domain.comment.PostCommentUseCase
import com.wynime.app.ui.comment.BangumiCommentSticker
import com.wynime.app.ui.comment.CommentEditorState
import com.wynime.app.ui.comment.CommentMapperContext
import com.wynime.app.ui.comment.CommentMapperContext.parseToUIComment
import com.wynime.app.ui.comment.CommentMapperContext.toCommentVoteValue
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.comment.EditCommentSticker
import com.wynime.app.ui.comment.UICommentSource
import com.wynime.app.ui.comment.reportSnapshotText
import com.wynime.app.ui.comment.toDataReason
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

abstract class PeopleDetailsViewModel(
    private val commentTarget: PersonCommentTarget,
    private val originalCommentsUrl: String,
) : AbstractViewModel(), KoinComponent {
    protected val repository: PersonDetailsRepository by inject()
    private val commentRepository: PersonCommentRepository by inject()
    private val commentReportService: WynimeCommentReportService by inject()
    private val postCommentUseCase: PostCommentUseCase by inject()

    protected val detailsRestarter = FlowRestarter()
    private val commentsRestarter = FlowRestarter()
    private val commentLoadFailureChannel = Channel<Throwable>(Channel.BUFFERED)

    protected abstract val commentPanelTitleFlow: Flow<String?>

    protected abstract val commentCountFlow: Flow<Int?>

    val comments: PeopleCommentsState by lazy { createComments() }

    private fun createComments(): PeopleCommentsState {
        val commentState = CommentState(
            list = commentRepository.commentsPager(
                commentTarget,

                onBangumiUnavailable = {
                    commentLoadFailureChannel.trySend(
                        RepositoryServiceUnavailableException("Bangumi person comments unavailable"),
                    )
                },
            )
                .map { page -> page.map { it.parseToUIComment() } }
                .restartable(commentsRestarter)
                .cachedIn(backgroundScope),
            countState = commentCountFlow.produceState(null),
            onSubmitCommentReaction = { comment, value, selected ->

                if (comment.source == UICommentSource.WYNIME) {
                    commentRepository.submitReaction(commentTarget, comment.sourceCommentId, value, selected)
                }
            },
            backgroundScope = backgroundScope,
            commentLoadFailures = commentLoadFailureChannel.receiveAsFlow(),
            onSubmitCommentVote = { comment, vote ->

                if (comment.source == UICommentSource.WYNIME) {
                    commentRepository.submitVote(commentTarget, comment.sourceCommentId, vote?.toCommentVoteValue())
                }
            },
        )

        val reportState = CommentReportState(
            onSubmitReport = { comment, reason, detail ->
                commentReportService.createReport(
                    targetType = when (commentTarget) {
                        is PersonCommentTarget.Person -> CommentReportTargetType.PERSON_COMMENT
                        is PersonCommentTarget.Character -> CommentReportTargetType.CHARACTER_COMMENT
                    },
                    targetId = comment.sourceCommentId,
                    reason = reason.toDataReason(),
                    commentAuthorId = comment.author?.id,
                    detail = detail.takeIf { it.isNotEmpty() },
                    contentSnapshot = comment.reportSnapshotText(),
                )
            },
            backgroundScope = backgroundScope,
        )

        val editorState = CommentEditorState(
            showExpandEditCommentButton = true,
            initialEditExpanded = false,
            panelTitle = commentPanelTitleFlow.produceState(null),
            stickers = flowOf(BangumiCommentSticker.map { EditCommentSticker(it.first, it.second) })
                .produceState(emptyList()),
            richTextRenderer = { text ->
                withContext(Dispatchers.Default) {
                    with(CommentMapperContext) { parseBBCode(text) }
                }
            },
            onSend = { context, content -> postCommentUseCase(context, content) },
            backgroundScope = backgroundScope,
        )

        return PeopleCommentsState(
            target = commentTarget,
            commentState = commentState,
            reportState = reportState,
            editorState = editorState,
            originalCommentsUrl = originalCommentsUrl,
            onRefresh = {
                commentsRestarter.restart()
                detailsRestarter.restart()
            },
        )
    }
}

class PersonDetailsViewModel(personId: Int) : PeopleDetailsViewModel(
    commentTarget = PersonCommentTarget.Person(personId),
    originalCommentsUrl = "https://bgm.tv/person/$personId",
) {
    val details = repository.personDetailsFlow(personId)
        .retryWithBackoff()
        .restartable(detailsRestarter)
        .stateInBackground(null)
    val castsPager = repository.personCastsPager(personId).cachedIn(backgroundScope)
    val worksPager = repository.personWorksPager(personId).cachedIn(backgroundScope)

    override val commentPanelTitleFlow: Flow<String?> = details.map { it?.person?.displayName }
    override val commentCountFlow: Flow<Int?> = details.map { it?.commentCount }
}

class CharacterDetailsViewModel(characterId: Int) : PeopleDetailsViewModel(
    commentTarget = PersonCommentTarget.Character(characterId),
    originalCommentsUrl = "https://bgm.tv/character/$characterId",
) {
    val details = repository.characterDetailsFlow(characterId)
        .retryWithBackoff()
        .restartable(detailsRestarter)
        .stateInBackground(null)
    val subjectsPager = repository.characterSubjectsPager(characterId).cachedIn(backgroundScope)

    override val commentPanelTitleFlow: Flow<String?> = details.map { it?.character?.displayName }
    override val commentCountFlow: Flow<Int?> = details.map { it?.commentCount }
}

private fun <T> Flow<T>.retryWithBackoff() = retryWhen { _, attempt ->
    delay(2.seconds * (attempt + 1).coerceAtMost(5).toInt())
    true
}
