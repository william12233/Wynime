@file:OptIn(TestOnly::class)

package com.wynime.app.ui.exploration.followed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.subject.FollowedSubjectInfo
import com.wynime.app.data.models.subject.TestFollowedSubjectInfos
import com.wynime.app.data.models.subject.listCoverUrl
import com.wynime.app.data.models.subject.hasNewEpisodeToPlay
import com.wynime.app.data.models.subject.preferredDisplayName
import com.wynime.app.data.models.subject.subjectInfo
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.layout.BasicCarouselItem
import com.wynime.app.ui.foundation.layout.CarouselItemDefaults
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isHeightAtLeastMedium
import com.wynime.app.ui.foundation.layout.isWidthAtLeastExpanded
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.layout.minimumHairlineSize
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.foundation.widgets.NsfwMask
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_followed_collect_as_to_show_here
import com.wynime.app.ui.lang.subject_collection_doing
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.app.ui.search.LoadErrorCardLayout
import com.wynime.app.ui.search.LoadErrorCardRole
import com.wynime.app.ui.search.isFinishedAndEmpty
import com.wynime.app.ui.search.isLoadingFirstPage
import com.wynime.app.ui.search.rememberLoadErrorState
import com.wynime.app.ui.search.rememberTestLazyPagingItems
import com.wynime.app.ui.subject.SubjectProgressState
import com.wynime.app.ui.subject.rememberSubjectStatusStrings
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun FollowedSubjectsLazyRow(
    items: LazyPagingItems<FollowedSubjectInfo>,

    onClick: (FollowedSubjectInfo) -> Unit,
    onPlay: (FollowedSubjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    layoutParameters: FollowedSubjectsLayoutParameters = FollowedSubjectsDefaults.layoutParameters(
        currentWindowAdaptiveInfo1(),
    ),
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalAlignment: Alignment.Vertical = Alignment.Top,
) {
    LazyRow(
        modifier,
        lazyListState,
        contentPadding,
        horizontalArrangement = layoutParameters.horizontalArrangement,
        verticalAlignment = verticalAlignment,
    ) {

        when {
            items.isLoadingFirstPage -> {

                items(8) {
                    FollowedSubjectItem(
                        null,
                        onClick = { },
                        onPlay = { },
                        layoutParameters.imageSize,
                        layoutParameters.shape,
                    )
                }
            }

            items.loadState.hasError -> {
                item {
                    Box(Modifier.minimumHairlineSize()) {
                        val problem by items.rememberLoadErrorState()
                        LoadErrorCard(problem, { items.retry() })
                    }
                }
            }

            items.isFinishedAndEmpty -> {
                item {
                    val followedHintText = stringResource(
                        Lang.exploration_followed_collect_as_to_show_here,
                        stringResource(Lang.subject_collection_doing),
                    )
                    Box(Modifier.minimumHairlineSize()) {
                        LoadErrorCardLayout(
                            LoadErrorCardRole.Unimportant,
                            content = {
                                ListItem(
                                    headlineContent = { Text(followedHintText) },
                                    colors = listItemColors,
                                )
                            },
                        )
                    }
                }
            }
        }
        items(
            items.itemCount,
            key = items.itemKey { "FollowedSubjectsLazyRow-" + it.subjectInfo.subjectId },
            contentType = items.itemContentType { it.subjectProgressInfo.hasNewEpisodeToPlay },
        ) { index ->
            val item = items[index]
            var subjectNsfwType: NsfwMode by rememberSaveable(item) {
                mutableStateOf(
                    item?.nsfwMode ?: NsfwMode.DISPLAY,
                )
            }

            NsfwMask(
                subjectNsfwType,
                onTemporarilyDisplay = { subjectNsfwType = NsfwMode.DISPLAY },
                shape = layoutParameters.shape,
            ) {
                FollowedSubjectItem(
                    item,
                    onClick = { item?.let { onClick(it) } },
                    onPlay = { item?.let { onPlay(it) } },
                    layoutParameters.imageSize,
                    layoutParameters.shape,
                )
            }
        }
    }
}

@Composable
private fun FollowedSubjectItem(
    item: FollowedSubjectInfo?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    imageSize: DpSize,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    BasicCarouselItem(
        label = { CarouselItemDefaults.Text(item?.subjectInfo?.preferredDisplayName(useOriginalTitle) ?: "") },
        modifier.placeholder(item == null, shape = shape),
        supportingText = {
            if (item != null) {
                val strings = rememberSubjectStatusStrings()
                val airingState = remember(item) {
                    SubjectProgressState(stateOf(item.subjectProgressInfo))
                }
                Text(airingState.buttonText(strings), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        maskShape = shape,
        overlay = {
            if (item?.subjectProgressInfo?.hasNewEpisodeToPlay == true) {
                FilledTonalIconButton(
                    onClick = { onPlay() },
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                }
            }
        },
    ) {
        if (item != null) {
            val image = @Composable {
                AsyncImage(
                    item.subjectInfo.listCoverUrl,
                    modifier = Modifier.size(imageSize),
                    contentDescription = item.subjectInfo.preferredDisplayName(useOriginalTitle),
                    contentScale = ContentScale.Crop,
                )
            }
            Surface({ onClick() }, content = image)
        } else {
            Box(Modifier.size(imageSize))
        }
    }
}

@Immutable
data class FollowedSubjectsLayoutParameters(
    val imageSize: DpSize,
    val horizontalArrangement: Arrangement.Horizontal,
    val shape: Shape,
)

@Stable
object FollowedSubjectsDefaults {
    @Composable
    fun layoutParameters(windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()): FollowedSubjectsLayoutParameters {
        val windowSizeClass = windowAdaptiveInfo.windowSizeClass
        return FollowedSubjectsLayoutParameters(
            imageSize = imageSize(windowAdaptiveInfo),
            horizontalArrangement = when {
                windowSizeClass.isWidthAtLeastExpanded -> Arrangement.spacedBy(16.dp)
                windowSizeClass.isWidthAtLeastMedium -> Arrangement.spacedBy(12.dp)
                else -> Arrangement.spacedBy(8.dp)
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    private fun imageSize(windowAdaptiveInfo: WindowAdaptiveInfo): DpSize {
        val windowSizeClass = windowAdaptiveInfo.windowSizeClass
        val baseSize = when {
            windowSizeClass.isHeightAtLeastMedium && windowSizeClass.isWidthAtLeastExpanded -> 160.dp
            windowSizeClass.isHeightAtLeastMedium && windowSizeClass.isWidthAtLeastMedium -> 140.dp
            else -> 120.dp
        }
        return DpSize(baseSize, (baseSize) / 9 * 16)
    }
}

@Composable
@PreviewLightDark
fun PreviewFollowedSubjectsLazyRow() = ProvideCompositionLocalsForPreview {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        FollowedSubjectsLazyRow(
            items = rememberTestLazyPagingItems(TestFollowedSubjectInfos),
            onClick = {},
            onPlay = {},
        )
    }
}
