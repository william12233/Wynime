package com.wynime.app.ui.subject.person

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import com.wynime.app.ui.comment.EditCommentSheet
import com.wynime.app.ui.lang.person_details_write_comment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import com.wynime.app.data.models.person.InfoboxRowInfo
import com.wynime.app.data.models.person.PersonSubjectSummary
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.navigation.SubjectDetailPlaceholder
import com.wynime.app.tools.formatDateTime
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.comment.UIComment
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.ImageViewer
import com.wynime.app.ui.foundation.avatar.AvatarImage
import com.wynime.app.ui.foundation.layout.rememberConnectedScrollState
import com.wynime.app.ui.foundation.rememberImageViewerHandler
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.richtext.RichTextDefaults
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_richtext_external_app_link_warning_prefix
import com.wynime.app.ui.lang.foundation_richtext_open_failed_prefix
import com.wynime.app.ui.lang.person_details_career_actor
import com.wynime.app.ui.lang.person_details_career_artist
import com.wynime.app.ui.lang.person_details_career_illustrator
import com.wynime.app.ui.lang.person_details_career_mangaka
import com.wynime.app.ui.lang.person_details_career_producer
import com.wynime.app.ui.lang.person_details_career_seiyu
import com.wynime.app.ui.lang.person_details_career_writer
import com.wynime.app.ui.lang.person_details_comments
import com.wynime.app.ui.lang.person_details_comments_count
import com.wynime.app.ui.lang.person_details_meta
import com.wynime.app.ui.lang.person_details_no_comments
import com.wynime.app.ui.lang.person_details_person
import com.wynime.app.ui.lang.person_details_role_character
import com.wynime.app.ui.lang.person_details_role_mecha
import com.wynime.app.ui.lang.person_details_role_organization
import com.wynime.app.ui.lang.person_details_role_ship
import com.wynime.app.ui.lang.subject_details_view_all
import com.wynime.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import com.wynime.app.ui.subject.details.components.SubjectCommentColumn
import com.wynime.app.ui.subject.details.components.SubjectDetailsDefaults
import com.wynime.app.ui.subject.details.sections.SectionHeader
import com.wynime.app.ui.subject.details.sections.groupThousands
import com.wynime.app.ui.subject.details.sections.toPlainText
import org.jetbrains.compose.resources.stringResource

@Immutable
class PeopleDetailsNavigation(
    val onClickPerson: (personId: Int) -> Unit,
    val onClickCharacter: (characterId: Int) -> Unit,
    val onClickSubject: (PersonSubjectSummary) -> Unit,
)

@Composable
fun rememberPeopleDetailsNavigation(onBeforeNavigate: () -> Unit = {}): PeopleDetailsNavigation {
    val navigator = LocalNavigator.current
    return remember(navigator, onBeforeNavigate) {
        PeopleDetailsNavigation(
            onClickPerson = {
                onBeforeNavigate()
                navigator.navigatePersonDetails(it)
            },
            onClickCharacter = {
                onBeforeNavigate()
                navigator.navigateCharacterDetails(it)
            },
            onClickSubject = { subject ->
                onBeforeNavigate()
                navigator.navigateSubjectDetails(
                    subject.subjectId,
                    placeholder = SubjectDetailPlaceholder(
                        id = subject.subjectId,
                        name = subject.name,
                        nameCN = subject.nameCn,
                        coverUrl = subject.imageLarge,
                    ),
                )
            },
        )
    }
}

@Composable
internal fun personKindLabel(career: List<String>): String {
    for (c in career) {
        val res = when (c) {
            "seiyu" -> Lang.person_details_career_seiyu
            "producer" -> Lang.person_details_career_producer
            "mangaka" -> Lang.person_details_career_mangaka
            "artist" -> Lang.person_details_career_artist
            "writer" -> Lang.person_details_career_writer
            "illustrator" -> Lang.person_details_career_illustrator
            "actor" -> Lang.person_details_career_actor
            else -> null
        }
        if (res != null) return stringResource(res)
    }
    return stringResource(Lang.person_details_person)
}

@Composable
internal fun characterRoleLabel(role: Int): String = stringResource(
    when (role) {
        2 -> Lang.person_details_role_mecha
        3 -> Lang.person_details_role_ship
        4 -> Lang.person_details_role_organization
        else -> Lang.person_details_role_character
    },
)

@Composable
internal fun peopleMetaLine(kindLabel: String, collects: Int): String =
    stringResource(Lang.person_details_meta, kindLabel, remember(collects) { groupThousands(collects) })

@Composable
internal fun PeopleHeaderRow(
    imageUrl: String?,
    displayName: String,
    originalName: String?,
    metaLine: String,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
    onClickImage: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(110.dp, 147.dp)
                .clip(MaterialTheme.shapes.medium)
                .then(if (onClickImage != null) Modifier.clickable(onClick = onClickImage) else Modifier)
                .placeholder(isPlaceholder),
        ) {
            AvatarImage(
                imageUrl,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                displayName,
                Modifier.placeholder(isPlaceholder),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!originalName.isNullOrBlank() && originalName != displayName) {
                Text(
                    originalName,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                metaLine,
                Modifier.padding(top = 8.dp).placeholder(isPlaceholder),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun PeopleTitleBlock(
    displayName: String,
    originalName: String?,
    metaLine: String,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            displayName,
            Modifier.placeholder(isPlaceholder),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!originalName.isNullOrBlank() && originalName != displayName) {
            Text(
                originalName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            metaLine,
            Modifier.placeholder(isPlaceholder),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PeopleInfoTable(
    rows: List<InfoboxRowInfo>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 78.dp,
    rowSpacing: Dp = 12.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    row.key,
                    Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.value,
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun PeopleSubjectCard(
    subject: PersonSubjectSummary,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 96.dp,
) {
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO).clip(MaterialTheme.shapes.small)) {
            AvatarImage(subject.imageLarge, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Text(
            subject.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal val PersonCastCardWidth = 96.dp

@Composable
internal fun PeoplePortraitCard(
    imageUrl: String?,
    name: String,
    caption: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = PersonCastCardWidth,
    circleCrop: Boolean = false,
) {
    Column(
        modifier
            .width(width)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = if (circleCrop) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (circleCrop) {
            Box(Modifier.size(width).clip(CircleShape)) {
                AvatarImage(
                    imageUrl,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }
        } else {
            Box(Modifier.size(width, width * 4 / 3).clip(MaterialTheme.shapes.small)) {
                AvatarImage(
                    imageUrl,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun <T : Any> PeopleStripSection(
    title: String,
    items: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
    itemSpacing: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.itemCount == 0) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onViewAll != null) {
            SectionHeader(title, actionLabel = stringResource(Lang.subject_details_view_all), onAction = onViewAll)
        } else {
            SectionHeader(title)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(itemSpacing)) {
            items(items.itemCount) { i ->
                val item = items[i] ?: return@items
                itemContent(item)
            }
        }
    }
}

@Composable
internal fun PersonCommentsSection(
    state: CommentState,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
    maxPreviewItems: Int = 3,
) {
    val comments = state.list.collectAsLazyPagingItemsWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.person_details_comments),
            actionLabel = state.count?.takeIf { it > 0 }
                ?.let { stringResource(Lang.person_details_comments_count, remember(it) { groupThousands(it) }) }
                ?: stringResource(Lang.subject_details_view_all),
            onAction = onShowAll,
        )
        val previewCount = minOf(comments.itemCount, maxPreviewItems)
        if (previewCount == 0) {
            Text(
                stringResource(Lang.person_details_no_comments),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (i in 0 until previewCount) {
            val comment = comments[i] ?: continue
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PersonCommentPreviewItem(
                comment,
                Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onShowAll),
            )
        }
    }
}

@Composable
private fun PersonCommentPreviewItem(
    comment: UIComment,
    modifier: Modifier = Modifier,
    maxTextLines: Int = 3,
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
                comment.author?.nickname ?: "",
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatDateTime(comment.createdAt, showTime = false),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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

@Composable
internal fun PersonCommentsSheet(
    comments: PeopleCommentsState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageViewer = rememberImageViewerHandler()
    ModalBottomSheet(
        onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PersonCommentsSheetContent(
            comments,
            onClickImage = { imageViewer.viewImage(it) },
        )
    }
    ImageViewer(imageViewer) { imageViewer.clear() }
}

@Composable
internal fun PersonCommentsSheetContent(
    comments: PeopleCommentsState,
    onClickImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = comments.commentState
    val browserNavigator = LocalUriHandler.current
    val toaster = LocalToaster.current
    val externalAppLinkWarningPrefix = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val openLinkFailedPrefix = stringResource(Lang.foundation_richtext_open_failed_prefix)

    var showEditor by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.count?.takeIf { it > 0 }?.let {
                    stringResource(Lang.person_details_comments) + " · " + remember(it) { groupThousands(it) }
                } ?: stringResource(Lang.person_details_comments),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(
                onClick = {
                    comments.startNewComment()
                    showEditor = true
                },
                Modifier.testTag(PersonCommentsSheetTestTags.WriteComment),
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = null, Modifier.size(18.dp))
                Text(
                    stringResource(Lang.person_details_write_comment),
                    Modifier.padding(start = 8.dp),
                )
            }
        }
        SubjectDetailsDefaults.SubjectCommentColumn(
            state = state,
            onClickUrl = { url ->
                RichTextDefaults.checkSanityAndOpen(
                    url,
                    browserNavigator,
                    toaster,
                    externalAppLinkWarningPrefix,
                    openLinkFailedPrefix,
                )
            },
            onClickImage = onClickImage,
            reportState = comments.reportState,
            onOpenOriginal = { browserNavigator.openUri(comments.originalCommentsUrl) },
            onClickReply = { comment ->
                comments.startReply(comment.sourceCommentId)
                showEditor = true
            },
            onToggleReaction = { comment, value -> state.submitReaction(comment, value) },
            connectedScrollState = rememberConnectedScrollState(),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        )
    }

    if (showEditor) {
        EditCommentSheet(
            state = comments.editorState,
            onDismiss = {
                showEditor = false
                comments.editorState.cancelSend()
            },
            onSendComplete = { comments.refresh() },
        )
    }
}

object PersonCommentsSheetTestTags {
    const val WriteComment = "PersonCommentsSheet.WriteComment"
}
