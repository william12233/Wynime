package com.wynime.app.ui.subject.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.data.models.subject.TestSubjectProgressInfos
import com.wynime.app.data.models.subject.preferredDisplayName
import com.wynime.app.data.models.subject.listCoverUrl
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthCompact
import com.wynime.app.ui.foundation.layout.plus
import com.wynime.app.ui.lang.*
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.app.ui.search.isLoadingNextPage
import com.wynime.app.ui.subject.AiringLabel
import com.wynime.app.ui.subject.AiringLabelState
import com.wynime.app.ui.subject.collection.components.EditCollectionTypeDropDown
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.app.ui.subject.collection.components.rememberTestEditableSubjectCollectionTypeState
import com.wynime.app.ui.subject.collection.progress.SubjectProgressButton
import com.wynime.app.ui.subject.collection.progress.rememberTestSubjectProgressState
import com.wynime.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.*

@Composable
fun SubjectCollectionsColumn(
    items: LazyPagingItems<SubjectCollectionInfo>,
    item: @Composable (item: SubjectCollectionInfo) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    enableAnimation: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val isCompact = currentWindowAdaptiveInfo1().windowSizeClass.isWidthCompact
    val spacedBy = if (isCompact) 16.dp else 24.dp
    val wynimeMotionScheme = LocalWynimeMotionScheme.current

    LazyVerticalGrid(
        GridCells.Adaptive(360.dp),
        modifier,
        gridState,
        contentPadding = contentPadding + PaddingValues(all = spacedBy / 2),
        userScrollEnabled = true,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(1.dp)) }

        if (items.loadState.hasError) {
            item {
                LoadErrorCard(
                    LoadError.fromCombinedLoadStates(items.loadState),
                    onRetry = { items.refresh() },
                    Modifier.padding(all = spacedBy / 2),
                )
            }
        }

        items(
            items.itemCount,
            items.itemKey { "SubjectCollectionsColumn-" + it.subjectId },
            contentType = items.itemContentType { it.progressInfo.nextEpisodeIdToPlay != null },
        ) { index ->
            items[index]?.let {
                Box(
                    Modifier
                        .padding(all = spacedBy / 2)
                        .ifThen(enableAnimation) {
                            animateItem(
                                fadeInSpec = wynimeMotionScheme.feedItemFadeInSpec,
                                placementSpec = wynimeMotionScheme.feedItemPlacementSpec,
                                fadeOutSpec = wynimeMotionScheme.feedItemFadeOutSpec,
                            )
                        },
                ) {
                    item(it)
                }
            }
        }

        if (items.isLoadingNextPage) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }

    }
}

@Composable
fun SubjectCollectionItem(
    item: SubjectCollectionInfo,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    onClick: () -> Unit,
    onShowEpisodeList: () -> Unit,
    playButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SubjectCollectionItemDefaults.height,
    shape: Shape = SubjectCollectionItemDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
) {
    Card(
        onClick,
        modifier.clip(shape).fillMaxWidth().height(height),
        shape = shape,
        colors = colors,
    ) {
        Row(Modifier.weight(1f, fill = false)) {
            AsyncImage(
                item.subjectInfo.listCoverUrl,
                contentDescription = null,
                modifier = Modifier
                    .height(height).width(height * COVER_WIDTH_TO_HEIGHT_RATIO),
                contentScale = ContentScale.Crop,
            )

            Box(Modifier.weight(1f)) {
                SubjectCollectionItemContent(
                    item = item,
                    editableSubjectCollectionTypeState = editableSubjectCollectionTypeState,
                    onShowEpisodeList = onShowEpisodeList,
                    playButton = playButton,
                    Modifier.padding(start = 12.dp).fillMaxSize(),
                )
            }
        }
    }
}

@Stable
object SubjectCollectionItemDefaults {
    val height: Dp get() = 148.dp
    val shape: Shape
        @Composable
        get() = MaterialTheme.shapes.small
}

@Composable
private fun SubjectCollectionItemContent(
    item: SubjectCollectionInfo,
    editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState,
    onShowEpisodeList: () -> Unit,
    playButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEditCollectionTypeMenu by remember { mutableStateOf(false) }

    Column(modifier) {

        Row(
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.subjectInfo.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    { showEditCollectionTypeMenu = true },
                    Modifier.fillMaxHeight().padding().testTag(SubjectCollectionItemTestTags.MoreButton),
                ) {
                    Icon(Icons.Outlined.MoreVert, null, Modifier.size(24.dp))
                }

                EditCollectionTypeDropDown(
                    editableSubjectCollectionTypeState,
                    expanded = showEditCollectionTypeMenu,
                    onDismissRequest = { showEditCollectionTypeMenu = false },
                    modifier = Modifier.testTag(SubjectCollectionItemTestTags.EditCollectionTypeMenu),
                )
            }
        }

        Row(
            Modifier.padding(top = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            AiringLabel(
                remember(item) {
                    AiringLabelState(stateOf(item.airingInfo), stateOf(item.progressInfo))
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .padding(vertical = 12.dp)
                .padding(horizontal = 12.dp)
                .align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onShowEpisodeList) {
                Text(stringResource(Lang.video_player_select_episode))
            }

            Box(Modifier.width(IntrinsicSize.Min)) { playButton() }
        }
    }
}

object SubjectCollectionItemTestTags {
    const val MoreButton = "SubjectCollectionItemMoreButton"
    const val EditCollectionTypeMenu = "SubjectCollectionItemEditCollectionTypeMenu"
}

@PreviewLightDark
@Composable
private fun PreviewSubjectCollectionsColumnPhone() {
    ProvideCompositionLocalsForPreview {
        SubjectCollectionsColumn(
            items = rememberTestItems(),
            item = { TestSubjectCollectionItem(it) },
        )
    }
}

@OptIn(TestOnly::class)
@Composable
private fun rememberTestItems() =
    remember { MutableStateFlow(PagingData.from(TestSubjectCollections)) }.collectAsLazyPagingItemsWithLifecycle()

@PreviewLightDark
@Composable
private fun PreviewSubjectCollectionsColumnEmptyButLoading() {
    ProvideCompositionLocalsForPreview {
        SubjectCollectionsColumn(
            items = rememberTestItems(),
            item = { TestSubjectCollectionItem(it) },
            Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewSubjectCollectionsColumnEmpty() {
    ProvideCompositionLocalsForPreview {
        SubjectCollectionsColumn(
            items = rememberTestItems(),
            item = { TestSubjectCollectionItem(it) },
            Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(TestOnly::class)
@Composable
private fun TestSubjectCollectionItem(it: SubjectCollectionInfo) {
    SubjectCollectionItem(
        item = it,
        editableSubjectCollectionTypeState = rememberTestEditableSubjectCollectionTypeState(),
        onClick = { },
        onShowEpisodeList = { },
        playButton = {
            SubjectProgressButton(
                state = rememberTestSubjectProgressState(
                    when (it.subjectId % 4) {
                        0 -> TestSubjectProgressInfos.NotOnAir
                        1 -> TestSubjectProgressInfos.ContinueWatching2
                        2 -> TestSubjectProgressInfos.Watched2
                        else -> TestSubjectProgressInfos.Done
                    },
                ),
                {},
            )
        },
    )
}

@Preview(
    heightDp = 1600, widthDp = 1600,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
@Preview(
    heightDp = 1600, widthDp = 1600,
)
@Composable
private fun PreviewSubjectCollectionsColumnDesktopLarge() {
    ProvideCompositionLocalsForPreview {
        SubjectCollectionsColumn(
            items = rememberTestItems(),
            item = { TestSubjectCollectionItem(it) },
        )
    }
}
