package com.wynime.app.ui.subject.details.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import com.kmpalette.color
import com.kmpalette.palette.graphics.Palette
import kotlinx.collections.immutable.toImmutableList
import com.wynime.app.data.models.subject.RelatedSubjectInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.data.models.subject.preferredDisplayName
import com.wynime.app.tools.ColorUtils
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.WynimeImageLoadSuccess
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.text.ProvideContentColor
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.rating_self_score
import com.wynime.app.ui.lang.subject_details_episodes
import com.wynime.app.ui.lang.subject_details_info
import com.wynime.app.ui.lang.subject_details_login_to_collect
import com.wynime.app.ui.lang.subject_details_rate
import com.wynime.app.ui.lang.subject_details_rating
import com.wynime.app.ui.lang.subject_details_related_subjects
import com.wynime.app.ui.subject.AiringLabel
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import com.wynime.app.ui.subject.collection.progress.SubjectProgressButton
import com.wynime.app.ui.subject.details.components.AnimatedGradientBackground
import com.wynime.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import com.wynime.app.ui.subject.details.components.RatingHistogram
import com.wynime.app.ui.subject.details.components.SUBJECT_COVER_IMAGE_TEST_TAG
import com.wynime.app.ui.subject.details.components.RelatedSubjectsGrid
import com.wynime.app.ui.subject.details.components.rememberNavigateToRelatedSubject
import com.wynime.app.ui.subject.details.components.rememberNavigateToRelationGraph
import com.wynime.app.ui.subject.details.sections.CharactersSection
import com.wynime.app.ui.subject.details.sections.HotReviewsCardContent
import com.wynime.app.ui.subject.details.sections.PagedEpisodesGrid
import com.wynime.app.ui.subject.details.sections.ReviewsPreviewSection
import com.wynime.app.ui.subject.details.sections.SectionHeader
import com.wynime.app.ui.subject.details.sections.SectionHeaderRelationGraphButton
import com.wynime.app.ui.subject.details.sections.SectionHeaderCacheButton
import com.wynime.app.ui.subject.details.sections.StaffSection
import com.wynime.app.ui.subject.details.sections.SubjectCollectionStatsRow
import com.wynime.app.ui.subject.details.sections.SubjectInfoTable
import com.wynime.app.ui.subject.details.sections.SubjectRatingSummary
import com.wynime.app.ui.subject.details.sections.SubjectSummarySection
import com.wynime.app.ui.subject.details.sections.SubjectTagsSection
import com.wynime.app.ui.subject.details.state.SubjectDetailsState
import com.wynime.app.ui.subject.details.state.SubjectDetailsUiState
import com.wynime.app.ui.subject.details.state.rememberAiringLabelState
import com.wynime.app.ui.subject.details.state.rememberSubjectProgressState
import com.wynime.app.ui.subject.episode.list.EpisodeListItem
import com.wynime.app.ui.subject.renderSubjectSeason
import com.wynime.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubjectDetailsMultiColumnPage(
    state: SubjectDetailsState,
    selfInfo: SelfInfoUiState,
    layoutParams: SubjectDetailsLayoutParams,
    onPlay: (episodeId: Int) -> Unit,
    onEpisodeLongClick: (EpisodeListItem) -> Unit,
    onClickTag: (Tag) -> Unit,
    onClickLogin: () -> Unit,
    onShowComments: () -> Unit,
    onClickCache: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    backgroundPalette: Palette? = null,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    onCoverImageSuccess: (WynimeImageLoadSuccess) -> Unit = {},
    onClickCover: (() -> Unit)? = null,
) {
    val info = state.info ?: return
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
    val comments = state.subjectCommentState.list.collectAsLazyPagingItemsWithLifecycle()
    val commentCount = state.subjectCommentState.count

    MultiColumnScaffold(
        layoutParams,
        modifier,
        showTopBar,
        windowInsets,
        navigationIcon,
        onClickOpenExternal,
        topBarTitle = info.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
        backgroundOverlay = {
            val surfaceColor = MaterialTheme.colorScheme.surface
            val colors = remember(backgroundPalette) {
                backgroundPalette?.swatches
                    ?.map { ColorUtils.blendColor(it.color, surfaceColor, 0.85f) }
                    ?.toImmutableList()
            }
            if (colors != null) {
                AnimatedGradientBackground(
                    colors,
                    speed = 0.05,
                    modifier = Modifier.fillMaxSize(),
                )
            }

        },
    ) {

        SubjectSidebar(
            state = state,
            uiState = uiState,
            info = info,
            selfInfo = selfInfo,
            mainEpisodeCount = episodes.size,
            onPlay = onPlay,
            onClickTag = onClickTag,
            onClickLogin = onClickLogin,
            itemSpacing = layoutParams.sidebarItemSpacing,
            onCoverImageSuccess = onCoverImageSuccess,
            modifier = Modifier.width(layoutParams.sidebarWidth),
            onClickCover = onClickCover,
        )

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing),
        ) {
            SubjectTitleBlock(info, uiState)
            if (layoutParams.kind != SubjectDetailsPaneKind.EXPANDED) {
                SubjectRatingRow(state, showHistogram = layoutParams.showInlineRatingHistogram)
            }
            if (info.summary.isNotBlank()) {
                SubjectSummarySection(info.summary)
            }
            PagedEpisodesGrid(
                episodes = episodes,
                currentEpisodeId = currentEpisodeId,
                onEpisodeClick = { onPlay(it.episodeId) },
                onEpisodeLongClick = onEpisodeLongClick,
                header = { pager ->
                    SectionHeader(stringResource(Lang.subject_details_episodes)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionHeaderCacheButton(onClickCache, showLabel = layoutParams.showCacheButtonLabel)

                            pager?.invoke() ?: ProvideContentColor(MaterialTheme.colorScheme.onSurfaceVariant) {
                                AiringLabel(
                                    uiState.rememberAiringLabelState(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
            CharactersSection(exposedCharacters, allCharacters, totalCharactersCount)
            if (!layoutParams.showRail) {
                StaffSection(
                    exposedStaff,
                    allStaff,
                    totalStaffCount,
                    gridColumns = layoutParams.staffGridColumns,
                )
            }
            if (related.itemCount > 0) {
                SubjectRelatedBlock(state.subjectId, related)
            }
            if (!layoutParams.showRail) {
                ReviewsPreviewSection(comments, commentCount, onShowAll = onShowComments)
            }
        }

        if (layoutParams.showRail) {
            Column(
                Modifier.width(layoutParams.railWidth),
                verticalArrangement = Arrangement.spacedBy(layoutParams.railItemSpacing),
            ) {
                RailCard {
                    SectionHeader(stringResource(Lang.subject_details_rating)) {
                        EditRatingButton(uiState.rating.selfRatingInfo.score, onClick = { state.requestEditRating() })
                    }
                    SubjectRatingSummary(
                        info.ratingInfo,
                        Modifier.padding(top = 8.dp),
                        scoreStyle = MaterialTheme.typography.headlineMedium,
                        onClick = { state.requestEditRating() },
                    )
                    RatingHistogram(info.ratingInfo, Modifier.padding(top = 16.dp))
                }
                if (comments.itemCount > 0) {
                    RailCard {
                        HotReviewsCardContent(comments, commentCount, onShowAll = onShowComments)
                    }
                }
                if (exposedStaff.itemCount > 0) {
                    RailCard {
                        StaffSection(exposedStaff, allStaff, totalStaffCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiColumnScaffold(
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    topBarTitle: String? = null,
    scrollState: ScrollState = rememberScrollState(),
    backgroundOverlay: @Composable (PaddingValues) -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current

    val stickyTopBarVisible by remember(scrollState, density, layoutParams) {
        derivedStateOf {
            scrollState.value >
                    with(density) { (layoutParams.contentTopPadding + TITLE_LINE_HEIGHT).toPx() }
        }
    }
    val topAppBarWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    val topAppBarActions: @Composable RowScope.() -> Unit = {
        IconButton(onClickOpenExternal) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        }
    }

    Scaffold(
        modifier,
        topBar = {
            if (showTopBar) {
                Box {

                    TopAppBar(
                        title = {},
                        navigationIcon = navigationIcon,
                        actions = topAppBarActions,
                        colors = WynimeThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                        windowInsets = topAppBarWindowInsets,
                    )

                    WynimeAnimatedVisibility(stickyTopBarVisible && topBarTitle != null) {
                        TopAppBar(
                            title = {
                                Text(topBarTitle ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            navigationIcon = navigationIcon,
                            actions = topAppBarActions,
                            colors = WynimeThemeDefaults.topAppBarColors(
                                containerColor = WynimeThemeDefaults.navigationContainerColor,
                            ),
                            windowInsets = topAppBarWindowInsets,
                        )
                    }
                }
            }
        },
        containerColor = WynimeThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { scaffoldPadding ->
        backgroundOverlay(scaffoldPadding)
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(scrollState),
        ) {
            Row(

                Modifier.padding(
                    start = layoutParams.contentHorizontalPadding,
                    end = layoutParams.contentHorizontalPadding,
                    top = layoutParams.contentTopPadding,
                    bottom = layoutParams.contentBottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(layoutParams.columnSpacing),
                content = content,
            )
        }
    }
}

private val TITLE_LINE_HEIGHT = 32.dp

@Composable
internal fun SubjectDetailsMultiColumnPlaceholder(
    subjectInfo: SubjectInfo?,
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
) {
    MultiColumnScaffold(
        layoutParams,
        modifier,
        showTopBar,
        windowInsets,
        navigationIcon,
        onClickOpenExternal,
        topBarTitle = subjectInfo?.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
    ) {
        Column(
            Modifier.width(layoutParams.sidebarWidth),
            verticalArrangement = Arrangement.spacedBy(layoutParams.sidebarItemSpacing),
        ) {
            val coverModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO)
                .clip(RoundedCornerShape(16.dp))
            val coverUrl = subjectInfo?.imageLarge
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(coverUrl, contentDescription = null, coverModifier, contentScale = ContentScale.Crop)
            } else {
                Spacer(coverModifier.placeholder(true))
            }
            repeat(2) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .placeholder(true),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val title = subjectInfo?.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle)
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.width(240.dp).height(32.dp).placeholder(true))
            }
            Spacer(Modifier.width(160.dp).height(20.dp).placeholder(true))
            repeat(4) {
                Spacer(Modifier.fillMaxWidth().height(16.dp).placeholder(true))
            }
        }
        if (layoutParams.showRail) {
            Column(
                Modifier.width(layoutParams.railWidth),
                verticalArrangement = Arrangement.spacedBy(layoutParams.railItemSpacing),
            ) {
                repeat(2) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .placeholder(true),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectSidebar(
    state: SubjectDetailsState,
    uiState: SubjectDetailsUiState,
    info: SubjectInfo,
    selfInfo: SelfInfoUiState,
    mainEpisodeCount: Int,
    onPlay: (episodeId: Int) -> Unit,
    onClickTag: (Tag) -> Unit,
    onClickLogin: () -> Unit,
    itemSpacing: Dp,
    onCoverImageSuccess: (WynimeImageLoadSuccess) -> Unit,
    modifier: Modifier = Modifier,
    onClickCover: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(itemSpacing)) {

        AsyncImage(
            info.imageLarge,
            contentDescription = null,
            Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO)
                .clip(RoundedCornerShape(16.dp))
                .ifThen(onClickCover != null) { clickable(onClick = checkNotNull(onClickCover)) }
                .testTag(SUBJECT_COVER_IMAGE_TEST_TAG),
            contentScale = ContentScale.Crop,
            onSuccess = onCoverImageSuccess,
        )

        SubjectProgressButton(
            uiState.rememberSubjectProgressState(),
            onPlay = { uiState.progressInfo?.nextEpisodeIdToPlay?.let(onPlay) },
            Modifier.fillMaxWidth(),
        )

        if (selfInfo.isSessionValid == false) {
            OutlinedButton(onClickLogin, Modifier.fillMaxWidth()) {
                Text(stringResource(Lang.subject_details_login_to_collect))
            }
        } else {
            EditableSubjectCollectionTypeButton(
                uiState.collectionTypeEdit,
                state,
                Modifier.fillMaxWidth(),
            )
        }

        SubjectCollectionStatsRow(info.collectionStats)

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Lang.subject_details_info), style = MaterialTheme.typography.titleMedium)
            SubjectInfoTable(info, mainEpisodeCount = mainEpisodeCount.takeIf { it > 0 })
        }

        SubjectTagsSection(info.tags, onClickTag)
    }
}

@Composable
private fun SubjectTitleBlock(info: SubjectInfo, uiState: SubjectDetailsUiState) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    val primaryTitle = info.preferredDisplayName(useOriginalTitle)
    val secondaryTitle = if (useOriginalTitle) info.displayName else info.name
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    primaryTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secondaryTitle.isNotBlank() && secondaryTitle != primaryTitle) {
                    Text(
                        secondaryTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(
                renderSubjectSeason(info.airDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiringLabel(
                uiState.rememberAiringLabelState(),
                style = MaterialTheme.typography.bodyMedium,
                progressColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubjectRatingRow(state: SubjectDetailsState, showHistogram: Boolean) {
    val info = state.info ?: return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubjectRatingSummary(
            info.ratingInfo,
            onClick = { state.requestEditRating() },
        )
        if (showHistogram) {
            Spacer(Modifier.weight(1f))
            RatingHistogram(info.ratingInfo, Modifier.width(RATING_HISTOGRAM_WIDTH))
        }
    }
}

private val RATING_HISTOGRAM_WIDTH = 274.dp

@Composable
private fun EditRatingButton(selfScore: Int, onClick: () -> Unit) {
    TextButton(onClick) {
        Icon(
            Icons.Rounded.StarOutline,
            contentDescription = null,
            Modifier.size(18.dp),
        )
        Text(
            if (selfScore > 0) {
                stringResource(Lang.rating_self_score, selfScore)
            } else {
                stringResource(Lang.subject_details_rate)
            },
            Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SubjectRelatedBlock(subjectId: Int, related: LazyPagingItems<RelatedSubjectInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(stringResource(Lang.subject_details_related_subjects)) {
            SectionHeaderRelationGraphButton(rememberNavigateToRelationGraph(subjectId))
        }
        RelatedSubjectsGrid(related, onClick = rememberNavigateToRelatedSubject())
    }
}

@Composable
private fun RailCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {

        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp)) {
            content()
        }
    }
}
