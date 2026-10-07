package com.wynime.app.ui.comment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.wynime.app.tools.LocalTimeFormatter
import com.wynime.app.tools.TimeFormatter
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class CommentItemInteractionTest {
    private val rootTag = "testRoot"

    private fun androidx.compose.ui.test.ComposeUiTest.setItem(
        comment: UIComment,
        onClickReply: ((UIComment) -> Unit)? = null,
        onToggleVote: ((UIComment, UICommentVote) -> Unit)? = null,
        onToggleReaction: ((UIComment, String) -> Unit)? = null,
        menu: CommentMenuHandlers? = null,
    ) {
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(
                    LocalTimeFormatter provides TimeFormatter(
                        getTimeNow = { CommentItemTestFixtures.fixedNow },
                    ),
                ) {
                    CommentItem(
                        comment = comment,
                        onClickUrl = {},
                        onClickImage = {},
                        modifier = Modifier.testTag(rootTag),
                        onClickReply = onClickReply,
                        onToggleVote = onToggleVote,
                        onToggleReaction = onToggleReaction,
                        menu = menu,
                    )
                }
            }
        }
    }

    @Test
    fun `like button submits LIKE vote`() = runWynimeComposeUiTest {
        val votes = mutableListOf<UICommentVote>()
        setItem(
            comment = CommentItemTestFixtures.wynimeComment(),
            onToggleVote = { _, vote -> votes += vote },
        )
        onNodeWithTag(CommentItemTestTags.LikeButton).performClick()
        waitForIdle()
        assertEquals(listOf(UICommentVote.LIKE), votes)
    }

    @Test
    fun `dislike button submits DISLIKE vote`() = runWynimeComposeUiTest {
        val votes = mutableListOf<UICommentVote>()
        setItem(
            comment = CommentItemTestFixtures.wynimeComment(),
            onToggleVote = { _, vote -> votes += vote },
        )
        onNodeWithTag(CommentItemTestTags.DislikeButton).performClick()
        waitForIdle()
        assertEquals(listOf(UICommentVote.DISLIKE), votes)
    }

    @Test
    fun `bangumi comment shows no action buttons`() = runWynimeComposeUiTest {
        setItem(
            comment = CommentItemTestFixtures.bangumiComment(),
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
        )
        onAllNodesWithTag(CommentItemTestTags.Actions).assertCountEquals(0)
    }

    @Test
    fun `clicking comment body requests reply`() = runWynimeComposeUiTest {
        var replyRequested = false
        setItem(
            comment = CommentItemTestFixtures.wynimeComment(),
            onClickReply = { replyRequested = true },
        )
        onNodeWithTag(rootTag).performClick()
        waitForIdle()
        assertTrue(replyRequested)
    }

    @Test
    fun `clicking bangumi comment body does not request reply`() = runWynimeComposeUiTest {
        var replyRequested = false
        setItem(
            comment = CommentItemTestFixtures.bangumiComment(),
            onClickReply = { replyRequested = true },
        )
        onNodeWithTag(rootTag).performClick()
        waitForIdle()
        assertEquals(false, replyRequested)
    }

    @Test
    fun `more button opens context menu and report invokes handler`() = runWynimeComposeUiTest {
        var reported = false
        setItem(
            comment = CommentItemTestFixtures.wynimeComment(),
            onToggleVote = { _, _ -> },
            menu = CommentMenuHandlers(onReport = { reported = true }),
        )
        onNodeWithTag(CommentItemTestTags.MoreButton).performClick()
        waitForIdle()
        onNodeWithTag(CommentContextMenuTestTags.Report).performClick()
        waitForIdle()
        assertTrue(reported)
    }

    @Test
    fun `open original menu item hidden for ani comment`() = runWynimeComposeUiTest {
        setItem(
            comment = CommentItemTestFixtures.wynimeComment(),
            onToggleVote = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
        )
        onNodeWithTag(CommentItemTestTags.MoreButton).performClick()
        waitForIdle()
        onAllNodesWithTag(CommentContextMenuTestTags.OpenOriginal).assertCountEquals(0)
    }

    @Test
    fun `replies block expand invokes handler`() = runWynimeComposeUiTest {
        var expanded = false
        setItem(
            comment = CommentItemTestFixtures.wynimeComment(withReply = true),
            onClickReply = { expanded = true },
        )
        onNodeWithTag(CommentItemTestTags.RepliesBlock).performClick()
        waitForIdle()
        assertTrue(expanded)
    }

    @Test
    fun `bangumi comment menu has copy but no report`() = runWynimeComposeUiTest {

        setItem(
            comment = CommentItemTestFixtures.bangumiComment(),
            menu = CommentMenuHandlers(
                onOpenOriginal = {},
                onReport = null,
            ),
        )
        onNodeWithTag(rootTag).performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag(CommentContextMenuTestTags.CopyContent).assertExists()
        onAllNodesWithTag(CommentContextMenuTestTags.Report).assertCountEquals(0)
    }

    @Test
    fun `bangumi replies block does not fall back to reply`() = runWynimeComposeUiTest {
        var replyRequested = false
        setItem(
            comment = CommentItemTestFixtures.bangumiComment(withReply = true),
            onClickReply = { replyRequested = true },
        )
        onNodeWithTag(CommentItemTestTags.RepliesBlock).performClick()
        waitForIdle()
        assertEquals(false, replyRequested)
    }
}
