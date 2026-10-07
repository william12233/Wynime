package com.wynime.app.ui.subject.details.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import com.wynime.app.data.models.subject.RelatedSubjectInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_details_episodes
import com.wynime.app.ui.lang.subject_details_info
import com.wynime.app.ui.lang.subject_details_related_subjects
import com.wynime.app.ui.lang.subject_details_summary
import com.wynime.app.ui.lang.subject_details_view_all
import com.wynime.app.ui.subject.AiringLabel
import com.wynime.app.ui.subject.details.components.RelatedSubjectCard
import com.wynime.app.ui.subject.details.components.RelatedSubjectsLazyRow
import com.wynime.app.ui.subject.details.components.rememberNavigateToRelatedSubject
import com.wynime.app.ui.subject.details.components.rememberNavigateToRelationGraph
import com.wynime.app.ui.subject.details.sections.CharactersSection
import com.wynime.app.ui.subject.details.sections.EpisodesRow
import com.wynime.app.ui.subject.details.sections.SectionHeader
import com.wynime.app.ui.subject.details.sections.SectionHeaderActionButton
import com.wynime.app.ui.subject.details.sections.SectionHeaderCacheButton
import com.wynime.app.ui.subject.details.sections.SectionHeaderRelationGraphButton
import com.wynime.app.ui.subject.details.sections.StaffSection
import com.wynime.app.ui.subject.details.sections.SubjectInfoTable
import com.wynime.app.ui.subject.details.sections.SubjectSummarySection
import com.wynime.app.ui.subject.details.sections.SubjectTagsSection
import com.wynime.app.ui.subject.details.sections.ViewAllSheet
import com.wynime.app.ui.subject.details.state.SubjectDetailsState
import com.wynime.app.ui.subject.details.state.rememberAiringLabelState
import com.wynime.app.ui.subject.episode.list.EpisodeListItem
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompactDetailsTabContent(
    state: SubjectDetailsState,
    info: SubjectInfo,
    onPlay: (episodeId: Int) -> Unit,
    onEpisodeLongClick: (EpisodeListItem) -> Unit,
    onClickTag: (Tag) -> Unit,
    onShowEpisodeList: () -> Unit,
    onClickCache: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalPadding: Dp = 16.dp,
) {
    val uiState by state.uiState.collectAsStateWithLifecycle()
    val episodes = uiState.episodeListUiState.mainEpisodes
    val currentEpisodeId = remember(episodes) { episodes.firstOrNull { !it.isDoneOrDropped }?.episodeId }

    val exposedCharacters = state.exposedCharactersPager.collectAsLazyPagingItemsWithLifecycle()
    val allCharacters = state.charactersPager.collectAsLazyPagingItemsWithLifecycle()
    val totalCharactersCount = uiState.totalCharactersCount
    val exposedStaff = state.exposedStaffPager.collectAsLazyPagingItemsWithLifecycle()
    val allStaff = state.staffPager.collectAsLazyPagingItemsWithLifecycle()
    val totalStaffCount = uiState.totalStaffCount
    val related = state.relatedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()

    val horizontalPaddingValues = PaddingValues(horizontal = horizontalPadding)
    val horizontalPaddingModifier = Modifier.padding(horizontalPaddingValues)

    LazyColumn(
        modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        item("episodes") {
            if (episodes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        stringResource(Lang.subject_details_episodes),
                        horizontalPaddingModifier,
                    ) {
                        SectionHeaderCacheButton(onClickCache, showLabel = false)
                        SectionHeaderActionButton(onShowEpisodeList) {
                            AiringLabel(
                                uiState.rememberAiringLabelState(),
                                style = LocalTextStyle.current,
                                progressColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    EpisodesRow(
                        episodes,
                        currentEpisodeId = currentEpisodeId,
                        onEpisodeClick = { onPlay(it.episodeId) },
                        onEpisodeLongClick = onEpisodeLongClick,
                        contentPadding = horizontalPaddingValues,
                    )
                }
            }
        }

        if (info.summary.isNotBlank()) {
            item("summary") {
                Column(
                    horizontalPaddingModifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(Lang.subject_details_summary), style = MaterialTheme.typography.titleMedium)
                    SubjectSummarySection(info.summary)
                }
            }
        }

        item("tags") {
            SubjectTagsSection(info.tags, onClickTag, horizontalPaddingModifier)
        }

        item("info") {
            Column(
                horizontalPaddingModifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Lang.subject_details_info), style = MaterialTheme.typography.titleMedium)
                SubjectInfoTable(info, mainEpisodeCount = episodes.size.takeIf { it > 0 })
            }
        }

        item("characters") {
            CharactersSection(
                exposedCharacters,
                allCharacters,
                totalCharactersCount,
                contentPadding = horizontalPaddingValues,
                avatarSize = 56.dp,
                itemSpacing = 0.dp,
            )
        }

        item("staff") {
            StaffSection(
                exposedStaff,
                allStaff,
                totalStaffCount,
                horizontalPaddingModifier,
                gridColumns = 3,
            )
        }

        if (related.itemCount > 0) {
            item("related") {
                RelatedSubjectsCompactSection(
                    state.subjectId,
                    related,
                    headerModifier = horizontalPaddingModifier,
                    contentPadding = horizontalPaddingValues,
                )
            }
        }

        item("footer") {}
    }
}

@Composable
private fun RelatedSubjectsCompactSection(
    subjectId: Int,
    related: LazyPagingItems<RelatedSubjectInfo>,
    headerModifier: Modifier,
    contentPadding: PaddingValues,
) {
    val onClickRelated = rememberNavigateToRelatedSubject()
    val onClickRelationGraph = rememberNavigateToRelationGraph(subjectId)
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(Lang.subject_details_related_subjects), headerModifier) {
            SectionHeaderRelationGraphButton(onClickRelationGraph)
            SectionHeaderActionButton({ showAll = true }) {
                Text(stringResource(Lang.subject_details_view_all))
            }
        }
        RelatedSubjectsLazyRow(
            related,
            onClick = onClickRelated,
            contentPadding = contentPadding,
        )
    }
    if (showAll) {
        ViewAllSheet(
            title = stringResource(Lang.subject_details_related_subjects),
            items = related,
            onDismissRequest = { showAll = false },
            cellMinWidth = 104.dp,
        ) { item ->
            RelatedSubjectCard(item, onClick = { onClickRelated(item) })
        }
    }
}
