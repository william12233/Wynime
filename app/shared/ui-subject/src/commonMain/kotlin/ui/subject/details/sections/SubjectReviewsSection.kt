package com.wynime.app.ui.subject.details.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.wynime.app.tools.formatDateTime
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.comment.UIComment
import com.wynime.app.ui.comment.UIRichText
import com.wynime.app.ui.foundation.avatar.AvatarImage
import com.wynime.app.ui.foundation.layout.desktopTitleBar
import com.wynime.app.ui.foundation.layout.desktopTitleBarPadding
import com.wynime.app.ui.foundation.layout.rememberConnectedScrollState
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_details_hot_reviews
import com.wynime.app.ui.lang.subject_details_reviews_count
import com.wynime.app.ui.lang.subject_details_tab_comments
import com.wynime.app.ui.lang.subject_details_view_all
import com.wynime.app.ui.lang.subject_details_write_review
import com.wynime.app.ui.rating.FiveRatingStars
import com.wynime.app.ui.richtext.UIRichElement
import com.wynime.app.ui.subject.details.components.SubjectCommentColumn
import com.wynime.app.ui.subject.details.components.SubjectDetailsDefaults
import org.jetbrains.compose.resources.stringResource

private const val PREVIEW_COMMENT_COUNT = 2

@Composable
fun ReviewsPreviewSection(
    comments: LazyPagingItems<UIComment>,
    totalCount: Int?,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (comments.itemCount == 0) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.subject_details_tab_comments),
            actionLabel = reviewsCountLabel(totalCount),
            onAction = onShowAll,
        )
        BoxWithConstraints {
            val count = minOf(comments.itemCount, PREVIEW_COMMENT_COUNT)
            if (maxWidth < STACK_REVIEW_CARDS_BELOW_WIDTH) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(count) { i ->
                        comments[i]?.let { ReviewPreviewCard(it, onClick = onShowAll, Modifier.fillMaxWidth()) }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(count) { i ->
                        comments[i]?.let { ReviewPreviewCard(it, onClick = onShowAll, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val STACK_REVIEW_CARDS_BELOW_WIDTH = 480.dp

@Composable
private fun ReviewPreviewCard(comment: UIComment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ReviewPreviewItem(
            comment,
            Modifier.padding(12.dp),
            showTime = true,
            maxTextLines = 1,
        )
    }
}

@Composable
fun HotReviewsCardContent(
    comments: LazyPagingItems<UIComment>,
    totalCount: Int?,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            stringResource(Lang.subject_details_hot_reviews),
            actionLabel = reviewsCountLabel(totalCount),
            onAction = onShowAll,
        )
        repeat(minOf(comments.itemCount, PREVIEW_COMMENT_COUNT)) { i ->
            val comment = comments[i] ?: return@repeat
            if (i > 0) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            } else {
                Spacer(Modifier.size(8.dp))
            }
            ReviewPreviewItem(
                comment,
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onShowAll),
                maxTextLines = 2,
            )
        }
    }
}

@Composable
private fun reviewsCountLabel(totalCount: Int?): String =
    totalCount?.takeIf { it > 0 }
        ?.let { stringResource(Lang.subject_details_reviews_count, remember(it) { groupThousands(it) }) }
        ?: stringResource(Lang.subject_details_view_all)

@Composable
private fun ReviewPreviewItem(
    comment: UIComment,
    modifier: Modifier = Modifier,
    showTime: Boolean = false,
    maxTextLines: Int = 2,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvatarImage(
                comment.author?.avatarUrl,
                Modifier.size(24.dp).clip(CircleShape),
            )
            Text(
                comment.author?.nickname ?: comment.author?.id?.toString() ?: "",
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showTime) {
                Text(
                    formatDateTime(comment.createdAt, showTime = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            comment.rating?.takeIf { it > 0 }?.let { rating ->
                FiveRatingStars(rating, starSize = 12.dp)
            }
        }
        Text(
            remember(comment) { comment.content.toPlainText() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxTextLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun UIRichText.toPlainText(): String =
    elements.asSequence()
        .filterIsInstance<UIRichElement.AnnotatedText>()
        .flatMap { it.slice }
        .filterIsInstance<UIRichElement.Annotated.Text>()
        .joinToString("") { it.content }
        .trim()

@Composable
fun SubjectCommentsSheet(
    state: CommentState,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
    onClickWriteReview: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    reportState: CommentReportState? = null,
    onOpenOriginal: ((UIComment) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest,
        modifier = modifier.desktopTitleBarPadding().statusBarsPadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = {
            BottomSheetDefaults.windowInsets
                .add(WindowInsets.desktopTitleBar())
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Lang.subject_details_tab_comments),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClickWriteReview) {
                    Icon(Icons.Rounded.AddComment, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        stringResource(Lang.subject_details_write_review),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
            SubjectDetailsDefaults.SubjectCommentColumn(
                state = state,
                onClickUrl = onClickUrl,
                onClickImage = onClickImage,
                reportState = reportState,
                onOpenOriginal = onOpenOriginal,
                connectedScrollState = rememberConnectedScrollState(),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
