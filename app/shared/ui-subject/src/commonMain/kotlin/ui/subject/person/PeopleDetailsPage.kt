package com.wynime.app.ui.subject.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.chrisbanes.haze.rememberHazeState
import com.wynime.app.data.models.person.CharacterDetailsInfo
import com.wynime.app.data.models.person.CharacterSubjectInfo
import com.wynime.app.data.models.person.InfoboxRowInfo
import com.wynime.app.data.models.person.PersonCastInfo
import com.wynime.app.data.models.person.PersonDetailsInfo
import com.wynime.app.data.models.person.PersonWorkInfo
import com.wynime.app.data.models.subject.nameCn
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.ImageViewer
import com.wynime.app.ui.foundation.ImageViewerHandler
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.rememberImageViewerHandler
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.foundation.theme.LocalAppChromeHazeState
import com.wynime.app.ui.foundation.theme.appChromeFrostedGlass
import com.wynime.app.ui.foundation.theme.appChromeHazeSource
import com.wynime.app.ui.foundation.theme.isAppChromeFrostedGlassActive
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.person_details_basic_info
import com.wynime.app.ui.lang.person_details_casts
import com.wynime.app.ui.lang.person_details_character_subjects
import com.wynime.app.ui.lang.person_details_voice_actors
import com.wynime.app.ui.lang.person_details_works
import com.wynime.app.ui.lang.subject_details_summary
import com.wynime.app.ui.subject.details.components.PersonCard
import com.wynime.app.ui.subject.details.layout.SubjectDetailsLayoutParams
import com.wynime.app.ui.subject.details.sections.SectionHeader
import com.wynime.app.ui.subject.details.sections.SubjectSummarySection
import com.wynime.app.ui.subject.details.sections.ViewAllSheet
import org.jetbrains.compose.resources.stringResource

@Composable
fun PersonDetailsScreen(
    vm: PersonDetailsViewModel,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val details by vm.details.collectAsState()
    val casts = vm.castsPager.collectAsLazyPagingItems()
    val works = vm.worksPager.collectAsLazyPagingItems()

    PeopleDetailsScaffold(
        topBarTitle = details?.person?.displayName ?: "",
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        isPlaceholder = details == null,
        sidebarImageUrl = details?.person?.imageLarge,
        sidebarInfo = details?.infobox.orEmpty(),
        titleBlock = { isPlaceholder ->
            PeopleTitleBlock(
                displayName = details?.person?.displayName ?: "",
                originalName = details?.person?.name,
                metaLine = peopleMetaLine(personKindLabel(details?.career.orEmpty()), details?.collects ?: 0),
                isPlaceholder = isPlaceholder,
            )
        },
        summary = details?.person?.summary.orEmpty(),
        centerStrips = { PersonStrips(casts, works) },
        comments = vm.comments,
        compactContent = { imageViewer ->
            PersonDetailsContentColumn(
                details, casts, works, vm.comments,
                imageViewer = imageViewer,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun CharacterDetailsScreen(
    vm: CharacterDetailsViewModel,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    val details by vm.details.collectAsState()
    val subjects = vm.subjectsPager.collectAsLazyPagingItems()

    PeopleDetailsScaffold(
        topBarTitle = details?.character?.displayName ?: "",
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
        isPlaceholder = details == null,
        sidebarImageUrl = details?.character?.imageLarge,
        sidebarInfo = details?.infobox.orEmpty(),
        titleBlock = { isPlaceholder ->
            PeopleTitleBlock(
                displayName = details?.character?.displayName ?: "",
                originalName = details?.character?.name,
                metaLine = peopleMetaLine(characterRoleLabel(details?.role ?: 1), details?.collects ?: 0),
                isPlaceholder = isPlaceholder,
            )
        },
        summary = details?.summary.orEmpty(),
        centerStrips = { CharacterStrips(details, subjects) },
        comments = vm.comments,
        compactContent = { imageViewer ->
            CharacterDetailsContentColumn(
                details, subjects, vm.comments,
                imageViewer = imageViewer,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun PeopleDetailsScaffold(
    topBarTitle: String,
    navigationIcon: @Composable () -> Unit,
    windowInsets: WindowInsets,
    isPlaceholder: Boolean,
    sidebarImageUrl: String?,
    sidebarInfo: List<InfoboxRowInfo>,
    titleBlock: @Composable (isPlaceholder: Boolean) -> Unit,
    summary: String,
    centerStrips: @Composable () -> Unit,
    comments: PeopleCommentsState,
    compactContent: @Composable (imageViewer: ImageViewerHandler) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllComments by rememberSaveable { mutableStateOf(false) }
    val imageViewer = rememberImageViewerHandler()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val layoutParams = SubjectDetailsLayoutParams.calculate(maxWidth)
        val scrollState = rememberScrollState()
        val density = LocalDensity.current

        val titleScrollOutHeight =
            if (layoutParams.isMultiColumn) MULTI_COLUMN_TITLE_HEIGHT else COMPACT_HEADER_TITLE_HEIGHT

        val contentTopPadding = if (layoutParams.isMultiColumn) layoutParams.contentTopPadding else 0.dp
        val stickyTopBarVisible by remember(scrollState, density, layoutParams) {
            derivedStateOf {
                scrollState.value >
                        with(density) { (contentTopPadding + titleScrollOutHeight).toPx() }
            }
        }
        val topAppBarWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        val backgroundColor = WynimeThemeDefaults.pageContentBackgroundColor
        val stickyTopBarColor = WynimeThemeDefaults.navigationContainerColor

        CompositionLocalProvider(LocalAppChromeHazeState provides rememberHazeState()) {
            val frostedGlassActive = isAppChromeFrostedGlassActive()
            Scaffold(
                topBar = {
                    Box {

                        TopAppBar(
                            title = {},
                            navigationIcon = navigationIcon,
                            colors = WynimeThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                            windowInsets = topAppBarWindowInsets,
                        )

                        WynimeAnimatedVisibility(stickyTopBarVisible && topBarTitle.isNotBlank()) {
                            TopAppBar(
                                title = {
                                    Text(topBarTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.appChromeFrostedGlass(
                                    enabled = frostedGlassActive,
                                    containerColor = stickyTopBarColor,
                                ),
                                navigationIcon = navigationIcon,
                                colors = if (frostedGlassActive) {
                                    WynimeThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent)
                                } else {
                                    WynimeThemeDefaults.topAppBarColors(containerColor = stickyTopBarColor)
                                },
                                windowInsets = topAppBarWindowInsets,
                            )
                        }
                    }
                },
                containerColor = backgroundColor,
                contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ) { padding ->
                if (!layoutParams.isMultiColumn) {
                    val layoutDirection = LocalLayoutDirection.current
                    Column(
                        Modifier
                            .fillMaxSize()

                            .appChromeHazeSource(backgroundColor = backgroundColor)

                            .padding(
                                start = padding.calculateStartPadding(layoutDirection),
                                end = padding.calculateEndPadding(layoutDirection),
                                bottom = padding.calculateBottomPadding(),
                            )
                            .verticalScroll(scrollState)
                            .padding(top = padding.calculateTopPadding())
                            .padding(horizontal = layoutParams.contentHorizontalPadding)
                            .padding(
                                top = contentTopPadding,
                                bottom = layoutParams.contentBottomPadding,
                            ),
                    ) {
                        compactContent(imageViewer)
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxSize()

                            .appChromeHazeSource(backgroundColor = backgroundColor)
                            .padding(padding)
                            .verticalScroll(scrollState)
                            .padding(
                                start = layoutParams.contentHorizontalPadding,
                                end = layoutParams.contentHorizontalPadding,
                                top = contentTopPadding,
                                bottom = layoutParams.contentBottomPadding,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(layoutParams.columnSpacing),
                    ) {

                        Column(
                            Modifier.width(layoutParams.sidebarWidth),
                            verticalArrangement = Arrangement.spacedBy(layoutParams.sidebarItemSpacing),
                        ) {

                            var coverAspect by remember(sidebarImageUrl) { mutableStateOf(340f / 482f) }
                            val onClickSidebarImage = imageViewer.viewImageOrNull(sidebarImageUrl)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(coverAspect.coerceIn(0.4f, 1.6f))
                                    .clip(MaterialTheme.shapes.medium)
                                    .then(
                                        if (onClickSidebarImage != null) {
                                            Modifier.clickable(onClick = onClickSidebarImage)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .placeholder(isPlaceholder),
                            ) {
                                AsyncImage(
                                    model = sidebarImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Fit,
                                    onSuccess = { result ->
                                        if (result.width > 0 && result.height > 0) {
                                            coverAspect = result.width.toFloat() / result.height
                                        }
                                    },
                                )
                            }
                            if (sidebarInfo.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        stringResource(Lang.person_details_basic_info),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    PeopleInfoTable(sidebarInfo)
                                }
                            }
                        }

                        Column(
                            Modifier.weight(1f).widthIn(max = 840.dp),
                            verticalArrangement = Arrangement.spacedBy(layoutParams.sectionSpacing),
                        ) {
                            titleBlock(isPlaceholder)
                            if (summary.isNotBlank()) {
                                SubjectSummarySection(summary)
                            }
                            centerStrips()
                            if (!layoutParams.showRail) {
                                PersonCommentsSection(comments.commentState, onShowAll = { showAllComments = true })
                            }
                        }

                        if (layoutParams.showRail) {
                            Surface(
                                Modifier.width(layoutParams.railWidth),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                PersonCommentsSection(
                                    comments.commentState,
                                    onShowAll = { showAllComments = true },

                                    Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        ImageViewer(imageViewer) { imageViewer.clear() }

        if (layoutParams.isMultiColumn) {
            PeopleCommentsHost(comments, showAllComments, onDismissAllComments = { showAllComments = false })
        }
    }
}

private fun ImageViewerHandler?.viewImageOrNull(imageUrl: String?): (() -> Unit)? {
    val handler = this ?: return null
    val url = imageUrl?.takeIf { it.isNotBlank() } ?: return null
    return { handler.viewImage(url) }
}

private val COMPACT_HEADER_TITLE_HEIGHT = 96.dp

private val MULTI_COLUMN_TITLE_HEIGHT = 40.dp

@Composable
internal fun PersonDetailsContentColumn(
    details: PersonDetailsInfo?,
    casts: LazyPagingItems<PersonCastInfo>,
    works: LazyPagingItems<PersonWorkInfo>,
    comments: PeopleCommentsState,
    modifier: Modifier = Modifier,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
    imageViewer: ImageViewerHandler? = null,
) {
    var showAllComments by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PeopleHeaderRow(
            imageUrl = details?.person?.imageLarge,
            displayName = details?.person?.displayName ?: "",
            originalName = details?.person?.name,
            metaLine = peopleMetaLine(personKindLabel(details?.career.orEmpty()), details?.collects ?: 0),
            isPlaceholder = details == null,
            onClickImage = imageViewer.viewImageOrNull(details?.person?.imageLarge),
        )
        details?.person?.summary?.takeIf { it.isNotBlank() }?.let { summaryText ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.subject_details_summary))
                SubjectSummarySection(summaryText)
            }
        }
        details?.infobox?.takeIf { it.isNotEmpty() }?.let { rows ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.person_details_basic_info))
                PeopleInfoTable(rows)
            }
        }
        PersonStrips(casts, works, navigation)
        PersonCommentsSection(comments.commentState, onShowAll = { showAllComments = true })
    }
    PeopleCommentsHost(comments, showAllComments, onDismissAllComments = { showAllComments = false })
}

@Composable
private fun PersonStrips(
    casts: LazyPagingItems<PersonCastInfo>,
    works: LazyPagingItems<PersonWorkInfo>,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
) {
    var showAllCasts by rememberSaveable { mutableStateOf(false) }
    var showAllWorks by rememberSaveable { mutableStateOf(false) }

    PeopleStripSection(
        stringResource(Lang.person_details_casts),
        casts,
        onViewAll = { showAllCasts = true },
    ) { cast ->
        PeoplePortraitCard(
            imageUrl = cast.character.imageMedium,
            name = cast.character.displayName,
            caption = cast.subject.displayName,
            onClick = { navigation.onClickCharacter(cast.character.id) },
        )
    }
    PeopleStripSection(
        stringResource(Lang.person_details_works),
        works,
        onViewAll = { showAllWorks = true },
    ) { work ->
        PeopleSubjectCard(
            subject = work.subject,
            caption = work.positions.firstNotNullOfOrNull { it.nameCn },
            onClick = { navigation.onClickSubject(work.subject) },
        )
    }

    if (showAllCasts) {
        ViewAllSheet(
            title = stringResource(Lang.person_details_casts),
            items = casts,
            onDismissRequest = { showAllCasts = false },
        ) { cast ->
            PersonCard(
                avatarUrl = cast.character.imageMedium,
                name = cast.character.displayName,
                relation = cast.subject.displayName,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { navigation.onClickCharacter(cast.character.id) },
            )
        }
    }
    if (showAllWorks) {
        ViewAllSheet(
            title = stringResource(Lang.person_details_works),
            items = works,
            onDismissRequest = { showAllWorks = false },
        ) { work ->
            PersonCard(
                avatarUrl = work.subject.imageLarge,
                name = work.subject.displayName,
                relation = work.positions.mapNotNull { it.nameCn }.distinct().joinToString("、"),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { navigation.onClickSubject(work.subject) },
            )
        }
    }
}

@Composable
internal fun CharacterDetailsContentColumn(
    details: CharacterDetailsInfo?,
    subjects: LazyPagingItems<CharacterSubjectInfo>,
    comments: PeopleCommentsState,
    modifier: Modifier = Modifier,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
    imageViewer: ImageViewerHandler? = null,
) {
    var showAllComments by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PeopleHeaderRow(
            imageUrl = details?.character?.imageLarge,
            displayName = details?.character?.displayName ?: "",
            originalName = details?.character?.name,
            metaLine = peopleMetaLine(characterRoleLabel(details?.role ?: 1), details?.collects ?: 0),
            isPlaceholder = details == null,
            onClickImage = imageViewer.viewImageOrNull(details?.character?.imageLarge),
        )
        details?.summary?.takeIf { it.isNotBlank() }?.let { summaryText ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.subject_details_summary))
                SubjectSummarySection(summaryText)
            }
        }
        details?.infobox?.takeIf { it.isNotEmpty() }?.let { rows ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(Lang.person_details_basic_info))
                PeopleInfoTable(rows)
            }
        }
        CharacterStrips(details, subjects, navigation)
        PersonCommentsSection(comments.commentState, onShowAll = { showAllComments = true })
    }
    PeopleCommentsHost(comments, showAllComments, onDismissAllComments = { showAllComments = false })
}

@Composable
private fun CharacterStrips(
    details: CharacterDetailsInfo?,
    subjects: LazyPagingItems<CharacterSubjectInfo>,
    navigation: PeopleDetailsNavigation = rememberPeopleDetailsNavigation(),
) {
    var showAllSubjects by rememberSaveable { mutableStateOf(false) }

    val actors = details?.character?.actors.orEmpty()
    if (actors.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(Lang.person_details_voice_actors))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (actor in actors) {
                    PeoplePortraitCard(
                        imageUrl = actor.imageMedium,
                        name = actor.displayName,
                        caption = null,
                        onClick = { navigation.onClickPerson(actor.id) },
                        width = 76.dp,
                        circleCrop = true,
                    )
                }
            }
        }
    }
    PeopleStripSection(
        stringResource(Lang.person_details_character_subjects),
        subjects,
        onViewAll = { showAllSubjects = true },
    ) { item ->
        PeopleSubjectCard(
            subject = item.subject,
            caption = item.role.nameCn,
            onClick = { navigation.onClickSubject(item.subject) },
        )
    }

    if (showAllSubjects) {
        ViewAllSheet(
            title = stringResource(Lang.person_details_character_subjects),
            items = subjects,
            onDismissRequest = { showAllSubjects = false },
        ) { item ->
            PersonCard(
                avatarUrl = item.subject.imageLarge,
                name = item.subject.displayName,
                relation = item.role.nameCn,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { navigation.onClickSubject(item.subject) },
            )
        }
    }
}
