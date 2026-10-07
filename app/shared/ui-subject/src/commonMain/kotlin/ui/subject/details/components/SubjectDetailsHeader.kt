package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.subject.ContinueWatchingStatus
import com.wynime.app.data.models.subject.SubjectAiringInfo
import com.wynime.app.data.models.subject.SubjectAiringKind
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.models.subject.TestCoverImage
import com.wynime.app.data.models.subject.TestSubjectInfo
import com.wynime.app.data.models.subject.preferredDisplayName
import com.wynime.app.ui.foundation.WynimeImageLoadSuccess
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.layout.paddingIfNotEmpty
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.rating.EditableRating
import com.wynime.app.ui.rating.EditableRatingActions
import com.wynime.app.ui.rating.TestEditableRatingUiState
import com.wynime.app.ui.subject.AiringLabelState
import com.wynime.app.ui.subject.TestSubjectAiringInfo
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import com.wynime.app.ui.subject.collection.components.rememberTestEditableSubjectCollectionTypeState
import com.wynime.app.ui.subject.collection.progress.rememberTestSubjectProgressState
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.PackedDate
import com.wynime.utils.platform.annotations.TestOnly

const val COVER_WIDTH_TO_HEIGHT_RATIO = 849 / 1200f

const val SUBJECT_COVER_IMAGE_TEST_TAG = "SubjectCoverImage"

@Composable
internal fun SubjectDetailsHeader(
    info: SubjectInfo?,
    coverImageUrl: String?,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionAction: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    rating: @Composable () -> Unit,
    onCoverImageSuccess: (WynimeImageLoadSuccess) -> Unit = {},
    modifier: Modifier = Modifier,
    onClickCover: (() -> Unit)? = null,
) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    val primaryTitle = info?.preferredDisplayName(useOriginalTitle) ?: ""
    val secondaryTitle = if (useOriginalTitle) info?.displayName else info?.name
    if (currentWindowAdaptiveInfo1().isWidthAtLeastMedium) {
        SubjectDetailsHeaderWide(
            coverImageUrl = coverImageUrl,
            title = {
                Text(
                    primaryTitle,

                )
            },
            seasonTags = {
                seasonTags()
            },
            collectionData = collectionData,
            collectionAction = collectionAction,
            selectEpisodeButton = selectEpisodeButton,
            rating = rating,
            onCoverImageSuccess = onCoverImageSuccess,
            modifier = modifier,
            onClickCover = onClickCover,
        )
    } else {
        SubjectDetailsHeaderCompact(
            coverImageUrl = coverImageUrl,
            title = { Text(primaryTitle) },
            subtitle = { Text(secondaryTitle ?: "") },
            seasonTags = { seasonTags() },
            collectionData = collectionData,
            collectionAction = collectionAction,
            selectEpisodeButton = selectEpisodeButton,
            rating = rating,
            onSuccess = onCoverImageSuccess,
            modifier = modifier,
            onClickCover = onClickCover,
        )
    }
}

@Composable
fun SubjectDetailsHeaderCompact(
    coverImageUrl: String?,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionAction: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    rating: @Composable () -> Unit,
    onSuccess: (WynimeImageLoadSuccess) -> Unit,
    modifier: Modifier = Modifier,
    onClickCover: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            val imageWidth = 140.dp

            Box(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .ifThen(onClickCover != null) { clickable(onClick = checkNotNull(onClickCover)) }
                    .testTag(SUBJECT_COVER_IMAGE_TEST_TAG),
            ) {
                AsyncImage(
                    model = coverImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(imageWidth)
                        .height(imageWidth / COVER_WIDTH_TO_HEIGHT_RATIO),
                    contentScale = ContentScale.Crop,
                    crossfadeDurationMillis = 300,
                    onSuccess = onSuccess,
                )
            }

            Column(
                Modifier.weight(1f, fill = true)
                    .padding(horizontal = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    var showSubtitle by remember { mutableStateOf(false) }
                    Box(Modifier.clickable { showSubtitle = !showSubtitle }) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            if (showSubtitle) {
                                subtitle()
                            } else {
                                title()
                            }
                        }
                    }

                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        seasonTags()
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        Modifier.requiredHeight(IntrinsicSize.Max).align(Alignment.End),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        rating()
                    }
                }
            }
        }

        Row(
            Modifier.padding(top = 16.dp).align(Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
            ) {
                collectionData()
            }
            collectionAction()
        }

        Box(Modifier.paddingIfNotEmpty(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
            selectEpisodeButton()
        }
    }
}

@Composable
fun SubjectDetailsHeaderWide(
    coverImageUrl: String?,
    title: @Composable () -> Unit,
    seasonTags: @Composable RowScope.() -> Unit,
    collectionData: @Composable () -> Unit,
    collectionAction: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    rating: @Composable () -> Unit,
    onCoverImageSuccess: (WynimeImageLoadSuccess) -> Unit,
    modifier: Modifier = Modifier,
    onClickCover: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(
            Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            val imageWidth = 220.dp

            Box(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .ifThen(onClickCover != null) { clickable(onClick = checkNotNull(onClickCover)) }
                    .testTag(SUBJECT_COVER_IMAGE_TEST_TAG),
            ) {
                AsyncImage(
                    model = coverImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(imageWidth)
                        .height(imageWidth / COVER_WIDTH_TO_HEIGHT_RATIO),
                    contentScale = ContentScale.Crop,
                    crossfadeDurationMillis = 300,
                    onSuccess = onCoverImageSuccess,
                )
            }

            Column(
                Modifier
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            SelectionContainer {
                                title()
                            }
                        }
                    }
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            seasonTags()
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier) {
                        rating()
                    }
                }
                Row(Modifier.align(Alignment.Start)) {
                    collectionData()
                }
                Row(
                    Modifier.align(Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    collectionAction()
                }
                Row(
                    Modifier.align(Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(IntrinsicSize.Min)) {
                        selectEpisodeButton()
                    }
                }
            }
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSubjectDetailsHeaderCompleted() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo,
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Done,
            nextEpisodeIdToPlay = null,
        ),
        subjectInfo = TestSubjectInfo,
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
fun PreviewSubjectDetailsHeaderCompletedLong() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo,
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Done,
            nextEpisodeIdToPlay = null,
        ),
        subjectInfo = TestSubjectInfo.copy(
            nameCn = "孤独摇滚".repeat(20),
        ),
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSubjectDetailsHeaderOnAirWatched() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo.copy(
            kind = SubjectAiringKind.ON_AIR,
            mainEpisodeCount = 24,
            latestSort = EpisodeSort(20),
        ),
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Watched(
                episodeEp = EpisodeSort(20 - 12),
                episodeSort = EpisodeSort(20),
                PackedDate.Invalid,
            ),
            nextEpisodeIdToPlay = null,
        ),
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewSubjectDetailsHeaderOnAirContinue() {
    PreviewSubjectDetailsHeader(
        airingInfo = TestSubjectAiringInfo.copy(
            kind = SubjectAiringKind.ON_AIR,
            mainEpisodeCount = 24,
            latestSort = EpisodeSort(20),
        ),
        progressInfo = SubjectProgressInfo(
            continueWatchingStatus = ContinueWatchingStatus.Continue(
                episodeEp = EpisodeSort(20 - 12),
                episodeSort = EpisodeSort(20),
                watchedEpisodeEp = EpisodeSort(19 - 12),
                watchedEpisodeSort = EpisodeSort(19),
            ),
            nextEpisodeIdToPlay = null,
        ),
    )
}

@OptIn(TestOnly::class)
@Composable
fun PreviewSubjectDetailsHeader(
    airingInfo: SubjectAiringInfo,
    progressInfo: SubjectProgressInfo,
    subjectInfo: SubjectInfo = TestSubjectInfo,
) = ProvideCompositionLocalsForPreview {
    SubjectDetailsHeader(
        subjectInfo,
        TestCoverImage,
        seasonTags = {
            SubjectDetailsDefaults.SeasonTag(
                subjectInfo.airDate,
                AiringLabelState(
                    remember { stateOf(airingInfo) },
                    remember { stateOf(progressInfo) },
                ),
            )
        },
        collectionData = {
            SubjectDetailsDefaults.CollectionData(
                collectionStats = subjectInfo.collectionStats,
            )
        },
        collectionAction = {
            EditableSubjectCollectionTypeButton(
                rememberTestEditableSubjectCollectionTypeState(),
            )
        },
        selectEpisodeButton = {
            SubjectDetailsDefaults.SelectEpisodeButtons(rememberTestSubjectProgressState(), {}, {})
        },
        rating = {
            EditableRating(TestEditableRatingUiState, EditableRatingActions.Noop)
        },
    )
}
