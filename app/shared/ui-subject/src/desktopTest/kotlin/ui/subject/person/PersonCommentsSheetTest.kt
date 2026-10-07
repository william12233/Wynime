package com.wynime.app.ui.subject.person

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.person.PersonCommentTarget
import com.wynime.app.domain.comment.CommentContext
import com.wynime.app.domain.comment.CommentSendResult
import com.wynime.app.ui.comment.CommentEditorState
import com.wynime.app.ui.comment.CommentItemTestTags
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.UIComment
import com.wynime.app.ui.comment.UICommentSource
import com.wynime.app.ui.comment.UIRichText
import com.wynime.app.ui.comment.createTestCommentState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.richtext.UIRichElement
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(TestOnly::class)
class PersonCommentsSheetTest {
    private val target = PersonCommentTarget.Person(42)

    private fun createState(scope: CoroutineScope, comments: List<UIComment>): PeopleCommentsState {
        return PeopleCommentsState(
            target = target,
            commentState = createTestCommentState(scope, comments),
            reportState = CommentReportState(onSubmitReport = { _, _, _ -> }, backgroundScope = scope),
            editorState = CommentEditorState(
                showExpandEditCommentButton = false,
                initialEditExpanded = false,
                panelTitle = stateOf("Person 42"),
                stickers = stateOf(emptyList()),
                richTextRenderer = { UIRichText(emptyList()) },
                onSend = { _, _ -> CommentSendResult.Ok },
                backgroundScope = scope,
            ),
            originalCommentsUrl = "https://bgm.tv/person/42",
            onRefresh = {},
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setSheet(comments: List<UIComment>): () -> PeopleCommentsState {
        lateinit var state: PeopleCommentsState
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                state = remember { createState(scope, comments) }
                PersonCommentsSheetContent(state, onClickImage = {})
            }
        }
        return { state }
    }

    @Test
    fun `write comment button starts a new comment for the target`() = runWynimeComposeUiTest {
        val state = setSheet(listOf(wynimeComment(), bangumiComment()))

        onNodeWithTag(PersonCommentsSheetTestTags.WriteComment).performClick()
        waitForIdle()

        assertEquals(CommentContext.PersonComment(target), state().editorState.currentSendTarget)
    }

    @Test
    fun `clicking an ani comment starts a reply to it`() = runWynimeComposeUiTest {
        val state = setSheet(listOf(wynimeComment(), bangumiComment()))
        assertNull(state().editorState.currentSendTarget)

        onNodeWithText(ANI_CONTENT, substring = true).performClick()
        waitForIdle()

        assertEquals(CommentContext.PersonCommentReply(target, "ani-uuid"), state().editorState.currentSendTarget)
    }

    @Test
    fun `bangumi comments are read-only`() = runWynimeComposeUiTest {
        val state = setSheet(listOf(bangumiComment()))

        onAllNodesWithTag(CommentItemTestTags.Actions).assertCountEquals(0)
        onNodeWithText(BANGUMI_CONTENT, substring = true).performClick()
        waitForIdle()

        assertNull(state().editorState.currentSendTarget)
    }

    private fun wynimeComment() = UIComment(
        id = 1,
        stableId = "ani:ani-uuid",
        author = UserInfo(id = "user-1", username = null, nickname = "Ani 用户"),
        content = text(ANI_CONTENT),
        createdAt = 1_700_000_000_000L,
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = 0,
        rating = null,
        source = UICommentSource.WYNIME,
        sourceCommentId = "ani-uuid",
        canReply = true,
        rawContent = ANI_CONTENT,
    )

    private fun bangumiComment() = UIComment(
        id = 2,
        stableId = "bangumi:2",
        author = UserInfo(id = "20", username = null, nickname = "Bangumi 用户"),
        content = text(BANGUMI_CONTENT),
        createdAt = 1_700_000_000_000L,
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = 0,
        rating = null,
        source = UICommentSource.BANGUMI,
        sourceCommentId = "2",
        canReply = false,
        rawContent = BANGUMI_CONTENT,
    )

    private fun text(content: String) = UIRichText(
        listOf(UIRichElement.AnnotatedText(listOf(UIRichElement.Annotated.Text(content)))),
    )

    private companion object {
        const val ANI_CONTENT = "这是一条 Ani 评论"
        const val BANGUMI_CONTENT = "这是一条 Bangumi 评论"
    }
}
