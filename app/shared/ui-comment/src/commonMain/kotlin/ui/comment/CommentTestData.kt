package com.wynime.app.ui.comment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import com.wynime.app.data.models.UserInfo
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.richtext.UIRichElement
import com.wynime.app.ui.search.createTestPager
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.currentTimeMillis
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

@Composable
@TestOnly
fun rememberTestCommentState(commentList: List<UIComment>): CommentState {
    val scope = rememberCoroutineScope()
    return remember {
        createTestCommentState(scope, commentList)
    }
}

@TestOnly
fun createTestCommentState(
    scope: CoroutineScope,
    commentList: List<UIComment> = generateUiComment(10),
) = CommentState(
    list = createTestPager(commentList),
    countState = stateOf(commentList.size),
    onSubmitCommentReaction = { _, _, _ -> },
    backgroundScope = scope,
)

@TestOnly
fun generateUiComment(
    size: Int,
    content: UIRichText = UIRichText(
        listOf(
            UIRichElement.AnnotatedText(
                listOf(
                    UIRichElement.Annotated.Text(
                        "${(0..1000).random()}Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                                "Integer nec odio. Praesent libero. Sed cursus ante dapibus diam. Sed nisi. Nulla " +
                                "quis sem at nibh elementum imperdiet.",
                    ),
                ),
            ),
        ),
    ),
    generateReply: Boolean = false
): List<UIComment> = buildList {
    repeat(size) { i ->
        add(
            UIComment(
                id = i.toLong(),
                stableId = "test:$i",
                content = content,
                createdAt = run {
                    currentTimeMillis() - (1..10000).random().minutes.inWholeMilliseconds
                },
                author = UserInfo(
                    id = (1..100).random().toString(),
                    username = "",
                    nickname = "nickname him188 $i",
                    avatarUrl = "https://picsum.photos/200/300",
                ),
                reactions = buildList {
                    repeat((0..8).random()) {
                        add(UICommentReaction("bgm${(1..125).random()}", (0..100).random(), Random.nextBoolean()))
                    }
                },
                briefReplies = if (generateReply) {
                    generateUiComment((0..3).random(), content, false)
                } else emptyList(),
                replyCount = (0..100).random(),
                rating = (0..10).random(),
                canReply = true,
            ),
        )
    }
}
