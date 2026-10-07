package com.wynime.app.ui.comment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wynime.app.tools.LocalTimeFormatter
import com.wynime.app.tools.TimeFormatter
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class CommentItemScreenshotTest {
    private val outDir: File =
        File(System.getProperty("wynime.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private val figmaPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

    private fun capture(
        name: String,
        width: Int = 372,
        height: Int = 300,
        content: @Composable () -> Unit,
    ) {
        val previousLocale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE)
        try {
            runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview(darkMode = DarkMode.LIGHT) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f),
                        LocalTimeFormatter provides TimeFormatter(
                            getTimeNow = { CommentItemTestFixtures.fixedNow },
                        ),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            content()
                        }
                    }
                }
            }
            waitForIdle()
            val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
                ?: error("Failed to encode screenshot $name")
            File(outDir, "$name.png").writeBytes(png)
            }
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun wynimeFullSlots() = capture("comment-ani-full", height = 260) {
        CommentItem(
            comment = CommentItemTestFixtures.wynimeComment(
                reactions = CommentItemTestFixtures.defaultReactions,
                withReply = true,
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    @Test
    fun wynimeUpvotedToggledSticker() = capture("comment-ani-upvoted", height = 200) {
        CommentItem(
            comment = CommentItemTestFixtures.wynimeComment(
                reactions = CommentItemTestFixtures.toggledReactions,
                selfVote = UICommentVote.LIKE,
                content = "一里上台救场的这段 solo 封神，“吉他英雄”名不虚传。",
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    @Test
    fun clippedReactions() = capture("comment-clipped", height = 200) {
        CommentItem(
            comment = CommentItemTestFixtures.wynimeComment(
                reactions = CommentItemTestFixtures.overflowingReactions,
                likeCount = 45,
                content = "片尾曲歌词翻译很用心，staff 表滚动那里还有彩蛋。",
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    @Test
    fun noReactionsWithReply() = capture("comment-no-reactions-reply", height = 220) {
        CommentItem(
            comment = CommentItemTestFixtures.wynimeComment(
                reactions = emptyList(),
                withReply = true,
                likeCount = 3,
                content = "下周武道馆场次的曲目单有人扒出来了吗？",
            ),
            onClickUrl = {},
            onClickImage = {},
            onClickReply = {},
            onToggleVote = { _, _ -> },
            onToggleReaction = { _, _ -> },
            menu = CommentMenuHandlers(onReport = {}),
            contentPadding = figmaPadding,
        )
    }

    @Test
    fun presetSubjectReviews() = capture("comment-preset-subject", height = 280) {
        Column {
            CommentItem(
                comment = CommentItemTestFixtures.bangumiComment(
                    nickname = "绿夜",
                    content = "趣味和感动兼存的优秀作品",
                    rating = 9,
                    hoursAgo = 2,
                ),
                onClickUrl = {},
                onClickImage = {},
                showRating = true,
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            CommentItem(
                comment = CommentItemTestFixtures.bangumiComment(
                    nickname = "澄谷染",
                    content = "不多说了，剧情不拖沓，角色人设塑造到位。不过感觉社恐塑造得太过火了，扣一分。",
                    rating = 8,
                    hoursAgo = 22,
                ),
                onClickUrl = {},
                onClickImage = {},
                showRating = true,
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
        }
    }

    @Test
    fun presetEpisodeComments() = capture("comment-preset-episode", height = 340) {
        Column {
            CommentItem(
                comment = CommentItemTestFixtures.wynimeComment(
                    reactions = listOf(UICommentReaction("bgm11", count = 3, selected = false)),
                    content = "一里上台救场的这段 solo 封神，“吉他英雄”名不虚传。",
                ),
                onClickUrl = {},
                onClickImage = {},
                onClickReply = {},
                onToggleVote = { _, _ -> },
                onToggleReaction = { _, _ -> },
                menu = CommentMenuHandlers(onReport = {}),
                contentPadding = figmaPadding,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            CommentItem(
                comment = CommentItemTestFixtures.bangumiComment(),
                onClickUrl = {},
                onClickImage = {},
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
        }
    }

    @Test
    fun presetPersonComments() = capture("comment-preset-person", height = 280) {
        Column {
            CommentItem(
                comment = CommentItemTestFixtures.personComment(
                    nickname = "卡拉彼丘",
                    content = "一里的呜咽和碎碎念都配得太有灵魂了，吉他手辛苦了。",
                    daysAgo = 3,
                ),
                onClickUrl = {},
                onClickImage = {},
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            CommentItem(
                comment = CommentItemTestFixtures.personComment(
                    nickname = "薄荷糖",
                    content = "从 WUG 一路走过来，终于等到属于她的代表作，live 也超强。",
                    daysAgo = 5,
                ),
                onClickUrl = {},
                onClickImage = {},
                menu = CommentMenuHandlers(onOpenOriginal = {}, onReport = {}),
                contentPadding = figmaPadding,
            )
        }
    }

    @Test
    fun reportSheetContent() = capture("comment-report-sheet", width = 360, height = 520) {
        CommentReportSheetContent(
            snapshotText = "凉山下：一里上台救场的这段 solo 封神，“吉他英雄”名不虚传。运镜和作画都在燃烧经费。",
            onSubmit = { _, _ -> },
            onCancel = {},
            modifier = Modifier.padding(24.dp),
        )
    }
}
