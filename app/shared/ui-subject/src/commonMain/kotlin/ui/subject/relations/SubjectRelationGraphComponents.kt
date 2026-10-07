package com.wynime.app.ui.subject.relations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.subject.SubjectRelationGraphBranch
import com.wynime.app.data.models.subject.SubjectRelationGraphMainNode
import com.wynime.app.data.models.subject.SubjectRelationGraphPlatform
import com.wynime.app.data.models.subject.SubjectRelationGraphSubject
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_collection_doing
import com.wynime.app.ui.lang.subject_collection_done
import com.wynime.app.ui.lang.subject_collection_dropped
import com.wynime.app.ui.lang.subject_collection_on_hold
import com.wynime.app.ui.lang.subject_collection_wish
import com.wynime.app.ui.lang.subject_relation_graph_episodes
import com.wynime.app.ui.lang.subject_relation_graph_label_current
import com.wynime.app.ui.lang.subject_relation_graph_ordinal
import com.wynime.app.ui.lang.subject_relation_graph_platform_movie
import com.wynime.app.ui.lang.subject_relation_graph_platform_special
import com.wynime.app.ui.lang.subject_relation_graph_show_less
import com.wynime.app.ui.lang.subject_relation_graph_show_more
import com.wynime.app.ui.subject.details.components.renderSubjectRelation
import com.wynime.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.stringResource

@Immutable
internal class TimelineColors(
    val reached: Color,
    val upcoming: Color,
    val currentRing: Color,
    val surface: Color,
)

internal object SubjectRelationGraphDefaults {
    @Composable
    fun timelineColors(): TimelineColors = TimelineColors(
        reached = MaterialTheme.colorScheme.primary,
        upcoming = MaterialTheme.colorScheme.outline,
        currentRing = MaterialTheme.colorScheme.primaryContainer,
        surface = MaterialTheme.colorScheme.surface,
    )

    val PosterWidth = 176.dp
    val PosterHeight = 248.dp

    val CompactPosterWidth = 96.dp
    val CompactPosterHeight = 136.dp
    val CompactCardPadding = 8.dp

    val BranchCoverWidth = 40.dp
    val BranchCoverHeight = 56.dp

    val BranchRailX = 12.dp

    val BranchIndent = 32.dp

    const val COLLAPSED_BRANCH_COUNT_COMPACT = 3
    const val COLLAPSED_BRANCH_COUNT_WIDE = 4
}

internal enum class TimelineDot { CURRENT, REACHED, UPCOMING }

internal enum class TimelineLine { NONE, REACHED, UPCOMING }

internal fun Modifier.timelineVertical(
    colors: TimelineColors,
    dotCenterY: Dp,
    dot: TimelineDot,
    lineBefore: TimelineLine,
    lineAfter: TimelineLine,
    tickEndX: Dp,
    centerX: Dp = 12.dp,
): Modifier = drawBehind {
    val center = Offset(centerX.toPx(), dotCenterY.toPx())
    drawTimelineLine(colors, lineBefore, Offset(center.x, 0f), center)
    drawTimelineLine(colors, lineAfter, center, Offset(center.x, size.height))
    val tick = if (dot == TimelineDot.UPCOMING) TimelineLine.UPCOMING else TimelineLine.REACHED
    drawTimelineLine(colors, tick, center, Offset(tickEndX.toPx(), center.y))
    drawTimelineDot(colors, dot, center)
}

internal fun Modifier.timelineHorizontal(
    colors: TimelineColors,
    dotCenterX: Dp,
    dot: TimelineDot,
    lineBefore: TimelineLine,
    lineAfter: TimelineLine,
): Modifier = drawBehind {
    val center = Offset(dotCenterX.toPx(), size.height / 2)
    drawTimelineLine(colors, lineBefore, Offset(0f, center.y), center)
    drawTimelineLine(colors, lineAfter, center, Offset(size.width, center.y))
    drawTimelineDot(colors, dot, center)
}

private fun Modifier.branchConnector(color: Color, topExtent: Dp, isLast: Boolean): Modifier = drawBehind {
    val railX = SubjectRelationGraphDefaults.BranchRailX.toPx()
    val endX = (SubjectRelationGraphDefaults.BranchIndent - 6.dp).toPx()
    val centerY = size.height / 2
    val radius = 8.dp.toPx()
    val path = Path().apply {
        moveTo(railX, -topExtent.toPx())
        lineTo(railX, centerY - radius)
        quadraticTo(railX, centerY, railX + radius, centerY)
        lineTo(endX, centerY)
        if (!isLast) {
            moveTo(railX, centerY - radius)
            lineTo(railX, size.height)
        }
    }

    drawPath(path, color, style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.drawTimelineLine(colors: TimelineColors, line: TimelineLine, start: Offset, end: Offset) {
    val color = when (line) {
        TimelineLine.NONE -> return
        TimelineLine.REACHED -> colors.reached
        TimelineLine.UPCOMING -> colors.upcoming.copy(alpha = 0.5f)
    }
    drawLine(color, start, end, strokeWidth = 2.dp.toPx())
}

private fun DrawScope.drawTimelineDot(colors: TimelineColors, dot: TimelineDot, center: Offset) {
    when (dot) {
        TimelineDot.CURRENT -> {
            drawCircle(colors.currentRing, 12.dp.toPx(), center)
            drawCircle(colors.reached, 7.dp.toPx(), center)
        }

        TimelineDot.REACHED -> drawCircle(colors.reached, 6.dp.toPx(), center)
        TimelineDot.UPCOMING -> {
            drawCircle(colors.surface, 6.dp.toPx(), center)
            drawCircle(colors.upcoming, 5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
internal fun SubjectRelationGraphPoster(
    node: SubjectRelationGraphMainNode,
    ordinal: Int?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = node.subject
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            SubjectCover(
                subject,
                Modifier.size(SubjectRelationGraphDefaults.PosterWidth, SubjectRelationGraphDefaults.PosterHeight)
                    .then(if (isCurrent) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
                shape,
            )
            CollectionTypeBadge(subject.collectionType, Modifier.padding(6.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MainNodeLabel(node, ordinal, isCurrent)
            Text(
                subject.displayName,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                renderPlatformAndEpisodes(subject, omitLabeledPlatform = node.isMinor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SubjectRelationGraphCompactCard(
    node: SubjectRelationGraphMainNode,
    ordinal: Int?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = node.subject
    Surface(
        onClick,
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
    ) {
        Row(
            Modifier.padding(SubjectRelationGraphDefaults.CompactCardPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubjectCover(
                subject,
                Modifier.size(
                    SubjectRelationGraphDefaults.CompactPosterWidth,
                    SubjectRelationGraphDefaults.CompactPosterHeight,
                ),
                RoundedCornerShape(10.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MainNodeLabel(node, ordinal, isCurrent)
                    CollectionTypeText(subject.collectionType)
                }
                Text(
                    subject.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        renderYear(subject),
                        renderPlatformAndEpisodes(subject, omitLabeledPlatform = node.isMinor).ifEmpty { null },
                    )
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MainNodeLabel(node: SubjectRelationGraphMainNode, ordinal: Int?, isCurrent: Boolean) {
    val label = when {
        ordinal != null -> stringResource(Lang.subject_relation_graph_ordinal, ordinal)
        else -> when (node.subject.platform) {
            SubjectRelationGraphPlatform.MOVIE -> stringResource(Lang.subject_relation_graph_platform_movie)
            SubjectRelationGraphPlatform.OVA -> "OVA"
            else -> stringResource(Lang.subject_relation_graph_platform_special)
        }
    }
    Text(
        if (isCurrent) stringResource(Lang.subject_relation_graph_label_current, label) else label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
internal fun SubjectRelationGraphBranchList(
    branches: List<SubjectRelationGraphBranch>,
    currentSubjectId: Int,
    seriesName: String,
    collapsedCount: Int,
    nameMaxLines: Int,
    onClick: (SubjectRelationGraphSubject) -> Unit,
    modifier: Modifier = Modifier,
    connectorTopExtent: Dp? = null,
) {
    if (branches.isEmpty()) return
    val canCollapse = branches.size > collapsedCount
    var expanded by rememberSaveable(branches, currentSubjectId) {
        mutableStateOf(branches.drop(collapsedCount).any { it.subject.subjectId == currentSubjectId })
    }
    val spacing = 6.dp

    val connectorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        val visible = if (canCollapse && !expanded) branches.take(collapsedCount) else branches
        visible.forEachIndexed { index, branch ->
            BranchRow(
                branch,
                name = remember(branch, seriesName) { branch.subject.displayName.removeSeriesPrefix(seriesName) },
                isCurrent = branch.subject.subjectId == currentSubjectId,
                nameMaxLines = nameMaxLines,
                onClick = { onClick(branch.subject) },
                if (connectorTopExtent == null) Modifier else Modifier.branchConnector(
                    connectorColor,
                    topExtent = if (index == 0) connectorTopExtent else spacing,
                    isLast = index == visible.lastIndex,
                ).padding(start = SubjectRelationGraphDefaults.BranchIndent),
            )
        }
        if (canCollapse) {
            TextButton(
                { expanded = !expanded },

                if (connectorTopExtent == null) Modifier else Modifier.padding(
                    start = SubjectRelationGraphDefaults.BranchIndent - TEXT_BUTTON_HORIZONTAL_PADDING,
                ),
            ) {
                Text(
                    if (expanded) {
                        stringResource(Lang.subject_relation_graph_show_less)
                    } else {
                        stringResource(Lang.subject_relation_graph_show_more, branches.size - collapsedCount)
                    },
                )
            }
        }
    }
}

@Composable
private fun BranchRow(
    branch: SubjectRelationGraphBranch,
    name: String,
    isCurrent: Boolean,
    nameMaxLines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = branch.subject
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SubjectRelationGraphDefaults.BranchCoverHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectCover(
            subject,
            Modifier.size(SubjectRelationGraphDefaults.BranchCoverWidth, SubjectRelationGraphDefaults.BranchCoverHeight),
            RoundedCornerShape(6.dp),
        )
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text(
                    listOfNotNull(
                        branch.relation?.let { renderSubjectRelation(it) },
                        renderYear(subject),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                renderCollectionType(subject.collectionType)?.let {
                    Text(
                        " · $it",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

internal fun String.removeSeriesPrefix(seriesName: String): String {
    if (seriesName.isEmpty() || !startsWith(seriesName)) return this
    val rest = substring(seriesName.length)
    if (rest.firstOrNull()?.isWhitespace() != true) return this
    return rest.trim().ifEmpty { this }
}

@Composable
private fun SubjectCover(subject: SubjectRelationGraphSubject, modifier: Modifier, shape: Shape) {
    Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        AsyncImage(
            subject.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = if (currentWynimeBuildConfig.isDebug) remember { ColorPainter(Color.Gray) } else null,
        )
    }
}

@Composable
private fun CollectionTypeBadge(type: UnifiedCollectionType, modifier: Modifier = Modifier) {
    val text = renderCollectionType(type) ?: return
    Box(modifier.background(PosterBadgeColor, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun CollectionTypeText(type: UnifiedCollectionType) {
    val text = renderCollectionType(type) ?: return
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (type == UnifiedCollectionType.DOING) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
    )
}

@Composable
private fun renderCollectionType(type: UnifiedCollectionType): String? = when (type) {
    UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_wish)
    UnifiedCollectionType.DOING -> stringResource(Lang.subject_collection_doing)
    UnifiedCollectionType.DONE -> stringResource(Lang.subject_collection_done)
    UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_on_hold)
    UnifiedCollectionType.DROPPED -> stringResource(Lang.subject_collection_dropped)
    UnifiedCollectionType.NOT_COLLECTED -> null
}

internal fun renderYear(subject: SubjectRelationGraphSubject): String? =
    subject.airDate.takeIf { it.isValid }?.year?.toString()

@Composable
private fun renderPlatformAndEpisodes(subject: SubjectRelationGraphSubject, omitLabeledPlatform: Boolean): String {
    val isLabeled = subject.platform == SubjectRelationGraphPlatform.MOVIE ||
            subject.platform == SubjectRelationGraphPlatform.OVA
    val platform = when (subject.platform.takeUnless { omitLabeledPlatform && isLabeled }) {
        SubjectRelationGraphPlatform.TV -> "TV"
        SubjectRelationGraphPlatform.OVA -> "OVA"
        SubjectRelationGraphPlatform.WEB -> "WEB"
        SubjectRelationGraphPlatform.MOVIE -> stringResource(Lang.subject_relation_graph_platform_movie)
        null -> null
    }
    val episodes = if (subject.episodeCount > 1) {
        stringResource(Lang.subject_relation_graph_episodes, subject.episodeCount)
    } else null
    return listOfNotNull(platform, episodes).joinToString(" · ")
}

private val PosterBadgeColor = Color(0xC7141218)

private val TEXT_BUTTON_HORIZONTAL_PADDING = 12.dp
