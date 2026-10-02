/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.data.models.episode.preferredDisplayName
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.models.subject.preferredDisplayName
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeRequest
import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.LocalBrowserNavigator
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.foundation.widgets.ModalSideSheet
import me.him188.ani.app.ui.foundation.widgets.rememberModalSideSheetState
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close_selector
import me.him188.ani.app.ui.lang.subject_episode_related_recommendations
import me.him188.ani.app.ui.lang.subject_episode_select_media_source
import me.him188.ani.app.ui.lang.subject_episode_wish_change_to
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSelectorView
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummary
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummaryBanner
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummaryCard
import me.him188.ani.app.ui.mediaselect.summary.createTestMediaSelectorSummaryAutoSelecting
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeDialogsHost
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.components.rememberTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.createTestAiringLabelState
import me.him188.ani.app.ui.subject.details.SubjectDetailsScreen
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.details.state.createTestSubjectDetailsLoader
import me.him188.ani.app.ui.subject.episode.EpisodePageLoadError
import me.him188.ani.app.ui.subject.episode.details.components.FavoriteIconButton
import me.him188.ani.app.ui.subject.episode.details.components.SubjectRecommendationCard
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectRecommendationClick
import me.him188.ani.utils.analytics.recordEvent
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Stable
class EpisodeDetailsState(
    val subjectInfo: State<SubjectInfo>,
    val airingLabelState: AiringLabelState,
    val recommendations: State<List<SubjectRecommendation>>,
    val subjectDetailsStateLoader: SubjectDetailsStateLoader,
) {
    private val subject by subjectInfo

    val subjectId by derivedStateOf { subject.subjectId }
//    var subjectDetailsState by mutableStateOf<SubjectDetailsState?>(null)
//    val subjectDetailsStateError: SearchProblem

    var showEpisodes: Boolean by mutableStateOf(false)
}

/**
 * 番剧详情内容, 包含条目的基本信息, 选集, 评分.
 *
 * has inner top padding 8.dp
 */
@Composable
fun EpisodeDetails(
    mediaSelectorSummary: MediaSelectorSummary,
    state: EpisodeDetailsState,
    fetchRequest: MediaFetchRequest?,
    onFetchRequestChange: (MediaFetchRequest) -> Unit,
    episodeCarouselState: EpisodeCarouselState,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    mediaSelectorState: MediaSelectorState,
    selfInfo: SelfInfoUiState,
    onSwitchEpisode: (Int) -> Unit,
    onRestartSource: (String) -> Unit,
    onClickLogin: () -> Unit,
    onClickTag: (Tag) -> Unit,
    onEpisodeCollectionUpdate: (SetEpisodeCollectionTypeRequest) -> Unit,
    loadError: EpisodePageLoadError?,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
) {
    var showSubjectDetails by rememberSaveable {
        mutableStateOf(false)
    }
    if (state.subjectId != 0) {
        val subjectDetailsState by state.subjectDetailsStateLoader.state
            .collectAsStateWithLifecycle()
        if (showSubjectDetails) {
            ModalBottomSheet(
                { showSubjectDetails = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = currentWindowAdaptiveInfo1().isWidthAtLeastMedium),
                modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                contentWindowInsets = {
                    BottomSheetDefaults.windowInsets
                        .add(WindowInsets.desktopTitleBar())
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                },
            ) {
                SubjectDetailsScreen(
                    subjectDetailsState,
                    selfInfo,
                    onPlay = onSwitchEpisode,
                    onLoadErrorRetry = { state.subjectDetailsStateLoader.retry() },
                    onClickTag = onClickTag,
                    onEpisodeCollectionUpdate = onEpisodeCollectionUpdate,
                    showTopBar = false,
                    showBlurredBackground = false,
                )
            }
        }
    }

    val context = LocalContext.current
    val browserNavigator = LocalBrowserNavigator.current

    var expandEpisodeList by rememberSaveable { mutableStateOf(false) }

    val subjectRecommendations by remember(state) { state.recommendations }
    val atLeastMedium = currentWindowAdaptiveInfo1().isWidthAtLeastMedium

    EditableSubjectCollectionTypeDialogsHost(editableSubjectCollectionTypeState)

    val navigator = LocalNavigator.current
    EpisodeDetailsScaffold(
        subjectTitle = {
            Row {
                Text(
                    state.subjectInfo.value.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        favoriteButton = {
            FavoriteIconButton(editableSubjectCollectionTypeState)
        },
        episodeInfo = if (atLeastMedium) {
            {
                episodeCarouselState.playingEpisode?.let {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${it.episodeInfo.sort}  " +
                                it.episodeInfo.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else null,
        loadError = {
            when (loadError) {
                is EpisodePageLoadError.SeriesError -> LoadErrorCard(
                    loadError.loadError,
                    onRetryLoad,
                )

                is EpisodePageLoadError.SubjectError -> LoadErrorCard(
                    loadError.loadError,
                    onRetryLoad,
                )

                null -> {}
            }
        },
        airingStatus = {
            if (currentWindowAdaptiveInfo1().isWidthAtLeastMedium) {
                AiringLabel(
                    state.airingLabelState,
                    Modifier.align(Alignment.CenterVertically),
                    style = LocalTextStyle.current,
                    progressColor = LocalContentColor.current,
                )
            }
        },
        /* subjectSuggestions = {
            // 推荐一些状态修改操作
            val editableSubjectCollectionTypePresentation by editableSubjectCollectionTypeState.presentationFlow.collectAsStateWithLifecycle()
            if (selfInfo.isSessionValid == true) {
                when (editableSubjectCollectionTypePresentation.selfCollectionType) {
                    // 2025-10-20 改为在标题显示这个
                    /*UnifiedCollectionType.NOT_COLLECTED -> {
                        SubjectCollectionTypeSuggestions.Collect(editableSubjectCollectionTypeState)
                    }*/

                    UnifiedCollectionType.WISH, UnifiedCollectionType.ON_HOLD -> {
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                            Text(
                                stringResource(Lang.subject_episode_wish_change_to),
                                Modifier.align(Alignment.CenterVertically),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // 一起换行
                            SubjectCollectionTypeSuggestions.MarkAsDoing(editableSubjectCollectionTypeState)
                            SubjectCollectionTypeSuggestions.MarkAsDropped(editableSubjectCollectionTypeState)
                        }
                    }

                    else -> {}
                }
            }
        },*/
        mediaSelectorItem = { innerPadding ->
            var showMediaSelector by rememberSaveable { mutableStateOf(false) }
            if (showMediaSelector) {
                val windowAdaptiveInfo = currentWindowAdaptiveInfo1()

                if (windowAdaptiveInfo.isWidthAtLeastMedium) {
                    val sheetState = rememberModalSideSheetState()
                    ModalSideSheet(
                        { showMediaSelector = false },
                        state = sheetState,
                        containerColor = BottomSheetDefaults.ContainerColor,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(300.dp, 400.dp)
                                .windowInsetsPadding(AniWindowInsets.safeDrawing),
                        ) {
                            TopAppBar(
                                title = {
                                    Text(
                                        stringResource(Lang.subject_episode_select_media_source),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                },
                                actions = {
                                    IconButton(
                                        onClick = { sheetState.close() },
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = stringResource(Lang.subject_episode_close_selector),
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = BottomSheetDefaults.ContainerColor,
                                ),
                            )
                            MediaSelectorView(
                                state = mediaSelectorState,
                                fetchRequest = fetchRequest,
                                onFetchRequestChange = onFetchRequestChange,
                                onRestartSource = onRestartSource,
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                    .fillMaxWidth(),
                                onClickItem = {
                                    mediaSelectorState.select(it)
                                    showMediaSelector = false
                                },
                                scrollable = true,
                            )
                        }
                    }
                } else {
                    val sheetState =
                        rememberModalBottomSheetState(skipPartiallyExpanded = windowAdaptiveInfo.isWidthAtLeastMedium)
                    ModalBottomSheet(
                        { showMediaSelector = false },
                        sheetState = sheetState,
                        modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                        contentWindowInsets = {
                            BottomSheetDefaults.windowInsets
                                .add(WindowInsets.desktopTitleBar())
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        },
                    ) {
                        MediaSelectorView(
                            state = mediaSelectorState,
                            fetchRequest = fetchRequest,
                            onFetchRequestChange = onFetchRequestChange,
                            onRestartSource = onRestartSource,
                            modifier = Modifier.padding(top = 12.dp)
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                            onClickItem = {
                                mediaSelectorState.select(it)
                                showMediaSelector = false
                            },
                            scrollable = sheetState.targetValue == SheetValue.Expanded,
                        )
                    }
                }
            }

            if (atLeastMedium) {
                MediaSelectorSummaryCard(
                    mediaSelectorSummary,
                    onClickManualSelect = { showMediaSelector = true },
                    Modifier.fillMaxWidth().padding(innerPadding),
                )
            } else {
                MediaSelectorSummaryBanner(
                    mediaSelectorSummary,
                    onClickSwitchSource = { showMediaSelector = true },
                    Modifier.fillMaxWidth().padding(innerPadding),
                )
            }
        },
        episodeListSection = {
            EpisodeListSection(
                episodeCarouselState = episodeCarouselState,
                expanded = expandEpisodeList,
                airingLabelState = state.airingLabelState,
                onToggleExpanded = { expandEpisodeList = !expandEpisodeList },
            )
        },
        subjectRecommendations = { horizontalPadding ->
            item("subject_recommendation_header") {
                SectionTitle {
                    Text(stringResource(Lang.subject_episode_related_recommendations))
                }
            }
            for (recommendation in subjectRecommendations) {
                item("subject_recommendation_${recommendation.uniqueId}") {
                    SubjectRecommendationCard(
                        {
                            val uri = recommendation.uri
                            val targetSubjectId = recommendation.subjectId?.toInt()
                            Analytics.recordEvent(SubjectRecommendationClick) {
                                targetSubjectId?.let { put("subject_id", it) }
                                uri?.let { put("target_uri", it) }
                            }
                            Analytics.recordEvent(SubjectEnter) {
                                put("source", "episode_recommendation")
                                targetSubjectId?.let { put("subject_id", it) }
                                uri?.let { put("target_uri", it) }
                            }
                            if (uri != null) {
                                browserNavigator.openBrowser(context, uri)
                            } else if (targetSubjectId != null) {
                                navigator.navigateSubjectDetails(
                                    targetSubjectId,
                                    SubjectDetailPlaceholder(
                                        id = targetSubjectId,
                                        name = recommendation.name,
                                        nameCN = recommendation.nameCn ?: "",
                                        coverUrl = recommendation.imageUrl,
                                    ),
                                )
                            }
                        },
                        recommendation,
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontalPadding)
                            .padding(bottom = 12.dp),
                    )
                }
            }
        },
        onExpandSubject = {
            showSubjectDetails = true
            state.subjectDetailsStateLoader.load(state.subjectId, state.subjectInfo.value)
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )

}

@Composable
private fun SectionTitle(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(
        modifier.padding(top = 12.dp, bottom = 8.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Row(Modifier.weight(1f)) {
                content()
            }
            Row(Modifier.padding(start = 16.dp)) {
                actions()
            }
        }
    }
}

/**
 * [subjectRecommendations] 是最底部的内容, 可以使用 [LazyListScope].
 */
@Composable
fun EpisodeDetailsScaffold(
    subjectTitle: @Composable () -> Unit,
    favoriteButton: @Composable () -> Unit,
    loadError: @Composable () -> Unit,
    airingStatus: @Composable (FlowRowScope.() -> Unit),
    mediaSelectorItem: @Composable (contentPadding: PaddingValues) -> Unit,
    episodeListSection: @Composable () -> Unit,
    subjectRecommendations: LazyListScope.(contentPadding: PaddingValues) -> Unit,
    onExpandSubject: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(all = 16.dp),
    episodeInfo: (@Composable () -> Unit)? = null,
) {
    val contentPaddingState by rememberUpdatedState(contentPadding)
    val layoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val horizontalPaddingValues by remember {
        derivedStateOf {
            PaddingValues(
                start = contentPaddingState.calculateStartPadding(layoutDirection),
                end = contentPaddingState.calculateStartPadding(layoutDirection),
            )
        }
    }
    val atLeastMedium = currentWindowAdaptiveInfo1().isWidthAtLeastMedium
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background),
    ) {
        item {
            val topPadding by remember {
                derivedStateOf {
                    (contentPaddingState.calculateTopPadding() - 8.dp).coerceAtLeast(0.dp)
                }
            }
            Spacer(Modifier.height(topPadding))
        }

        item("episode_detail_header") {
            // header
            Column(
                Modifier.padding(horizontalPaddingValues),
            ) {
                Row {
                    Box(
                        Modifier.padding(top = 8.dp, end = 8.dp)
                            .weight(1f)
                            .clickable(onClick = onExpandSubject),
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            SelectionContainer { subjectTitle() }
                        }
                    }
                    Row(Modifier.padding(start = 12.dp)) {
                        favoriteButton()
                    }
                }
            }
        }

        if (episodeInfo != null) {
            item("episode_detail_episode_info") {
                Row(
                    Modifier.padding(horizontalPaddingValues),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    episodeInfo()
                }
            }
        }

        item("episode_detail_load_error") {
            Row(Modifier.padding(horizontalPaddingValues).paddingIfNotEmpty(top = 12.dp)) {
                loadError()
            }
        }

        if (atLeastMedium) {
            item("episode_detail_airing_status") {
                SectionTitle(
                    Modifier.padding(top = 8.dp, bottom = 8.dp),
                ) {
                    FlowRow(
                        Modifier,/*.weight(1f)*/
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    ) {
                        airingStatus()
                    }
                }
            }
        }

        item("episode_detail_exposed_episode_item") {
            Row(Modifier) {
                mediaSelectorItem(horizontalPaddingValues)
            }
        }

        item("episode_detail_episode_list_section") {
            Box(Modifier.padding(top = if (atLeastMedium) 8.dp else 0.dp)) {
                episodeListSection()
            }
        }

        subjectRecommendations(horizontalPaddingValues)

        item("system_bar_spacer") {
            Spacer(
                Modifier.windowInsetsBottomHeight(
                    AniWindowInsets.safeDrawing,
                ).heightIn(min = Dp.Hairline),
            )
        }
    }
}


@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsLongTitle() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "中文条目名称啊中文条目名称中文条啊目名称中文条目名称中文条目名称中文",
            )
        },
    )
    PreviewEpisodeDetailsImpl(state)
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsShortTitle() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "小市民系列",
            )
        },
    )
    PreviewEpisodeDetailsImpl(state)
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsScroll() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "小市民系列",
            )
        },
    )
    Column(Modifier.height(300.dp)) {
        PreviewEpisodeDetailsImpl(state)
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview(name = "PC", device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewEpisodeDetailsPc() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "小市民系列",
            )
        },
    )
    PreviewEpisodeDetailsImpl(
        state,
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsDoing() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState(
        remember {
            SubjectInfo.Empty.copy(
                nameCn = "小市民系列",
            )
        },
    )
    PreviewEpisodeDetailsImpl(
        state,
        editableSubjectCollectionTypeState = rememberTestEditableSubjectCollectionTypeState(UnifiedCollectionType.DOING),
    )
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsNotAuthorized() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
        selfInfo = TestSelfInfoUiState,
    )
}

@Composable
@PreviewLightDark
fun PreviewEpisodeDetailsNotSelected() = ProvideCompositionLocalsForPreview {
    val state = rememberTestEpisodeDetailsState()
    PreviewEpisodeDetailsImpl(
        state,
    )
}

@OptIn(TestOnly::class)
@Composable
private fun rememberTestEpisodeDetailsState(
    subjectInfo: SubjectInfo = SubjectInfo.Empty.copy(
        nameCn = "中文条目名称啊中文条目名称中文条啊目名称中文条目名称中文条目名称中文",
    ),
): EpisodeDetailsState {
    val scope = rememberCoroutineScope()
    return remember {
        EpisodeDetailsState(
            subjectInfo = mutableStateOf(subjectInfo),
            airingLabelState = createTestAiringLabelState(),
            recommendations = mutableStateOf(PreviewSubjectRecommendations),
            subjectDetailsStateLoader = createTestSubjectDetailsLoader(scope),
        )
    }
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewEpisodeDetailsImpl(
    state: EpisodeDetailsState,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState = rememberTestEditableSubjectCollectionTypeState(),
    mediaSelectorState: MediaSelectorState = rememberTestMediaSelectorState(),
    selfInfo: SelfInfoUiState = TestSelfInfoUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold {
        EpisodeDetails(
            mediaSelectorSummary = createTestMediaSelectorSummaryAutoSelecting(),
            state,
            TestMediaFetchRequest,
            { },
            episodeCarouselState = remember {
                EpisodeCarouselState(
                    mutableStateOf(PreviewEpisodeCollections),
                    mutableStateOf(PreviewEpisodeCollections[1]),
                    cacheStatus = { EpisodeCacheStatus.NotCached },
                    onSelect = {},
                    onChangeCollectionType = { _, _ -> },
                    backgroundScope = PreviewScope,
                )
            },
            editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
            mediaSelectorState = mediaSelectorState,
            selfInfo = selfInfo,
            onSwitchEpisode = {},
            onRestartSource = {},
            onClickLogin = { },
            onClickTag = {},
            onEpisodeCollectionUpdate = {},
            null, {},
            modifier
                .padding(bottom = 16.dp, top = 8.dp)
                .padding(it),
        )
    }
}

