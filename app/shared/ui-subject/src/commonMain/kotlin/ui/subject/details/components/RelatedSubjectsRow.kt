package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import com.wynime.app.data.models.subject.RelatedSubjectInfo
import com.wynime.app.data.models.subject.SubjectRelation
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.navigation.SubjectDetailPlaceholder
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_details_relation_compilation
import com.wynime.app.ui.lang.subject_details_relation_derived
import com.wynime.app.ui.lang.subject_details_relation_main_story
import com.wynime.app.ui.lang.subject_details_relation_prequel
import com.wynime.app.ui.lang.subject_details_relation_sequel
import com.wynime.app.ui.lang.subject_details_relation_special
import com.wynime.app.ui.search.createTestPager
import com.wynime.app.ui.subject.details.TestRelatedSubjects
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun RelatedSubjectsGrid(
    items: LazyPagingItems<RelatedSubjectInfo>,
    onClick: (RelatedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 150.dp,
    spacing: Dp = 20.dp,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(items.itemCount) { i ->
            val item = items[i] ?: return@repeat
            RelatedSubjectCard(item, onClick = { onClick(item) }, Modifier.width(itemWidth))
        }
    }
}

@Composable
fun RelatedSubjectsLazyRow(
    items: LazyPagingItems<RelatedSubjectInfo>,
    onClick: (RelatedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 96.dp,
    spacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = contentPadding,
    ) {
        items(items.itemCount) { i ->
            items[i]?.let { item ->
                RelatedSubjectCard(item, onClick = { onClick(item) }, Modifier.width(itemWidth))
            }
        }
    }
}

@Composable
fun RelatedSubjectCard(
    info: RelatedSubjectInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            Modifier.fillMaxWidth().aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            AsyncImage(
                info.image,
                contentDescription = null,
                Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
                placeholder = if (currentWynimeBuildConfig.isDebug) remember { ColorPainter(Color.Gray) } else null,
            )
        }
        Column {
            Text(
                info.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            info.relation?.let { relation ->
                Text(
                    renderSubjectRelation(relation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun rememberNavigateToRelatedSubject(): (RelatedSubjectInfo) -> Unit {
    val navigator = LocalNavigator.current
    return remember(navigator) {
        { info ->
            navigator.navigateSubjectDetails(
                info.subjectId,
                placeholder = SubjectDetailPlaceholder(
                    id = info.subjectId,
                    name = info.name ?: "",
                    nameCN = info.nameCn,
                    coverUrl = info.image ?: "",
                ),
            )
        }
    }
}

@Composable
fun rememberNavigateToRelationGraph(subjectId: Int): () -> Unit {
    val navigator = LocalNavigator.current
    return remember(navigator, subjectId) {
        { navigator.navigateSubjectRelationGraph(subjectId) }
    }
}

@Composable
internal fun renderSubjectRelation(relation: SubjectRelation): String = when (relation) {
    SubjectRelation.PREQUEL -> stringResource(Lang.subject_details_relation_prequel)
    SubjectRelation.SEQUEL -> stringResource(Lang.subject_details_relation_sequel)
    SubjectRelation.DERIVED -> stringResource(Lang.subject_details_relation_derived)
    SubjectRelation.SPECIAL -> stringResource(Lang.subject_details_relation_special)
    SubjectRelation.MAIN_STORY -> stringResource(Lang.subject_details_relation_main_story)
    SubjectRelation.COMPILATION -> stringResource(Lang.subject_details_relation_compilation)
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewRelatedSubjectsGrid() = ProvideCompositionLocalsForPreview {
    Surface {
        RelatedSubjectsGrid(
            createTestPager(TestRelatedSubjects).collectAsLazyPagingItemsWithLifecycle(),
            onClick = {},
        )
    }
}
