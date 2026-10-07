package com.wynime.app.ui.subject.details.sections

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.subject.episode.list.EPISODE_STILL_TAG
import com.wynime.app.ui.subject.episode.list.EPISODE_PROGRESS_TAG
import com.wynime.app.ui.subject.episode.list.EPISODE_WATCHED_BADGE_TAG
import com.wynime.app.ui.subject.episode.list.EpisodeListItem
import com.wynime.app.ui.subject.episode.list.TestEpisodeStillUrl
import com.wynime.app.ui.subject.episode.list.createTestEpisodeListItem
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class EpisodesSectionTest {
    @Test
    fun `episode grid cell handles click and long click separately`() = runWynimeComposeUiTest {
        var clickCount = 0
        var longClickCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = createTestEpisodeListItem(),
                    isPlaying = false,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ },
                    modifier = Modifier.testTag(EPISODE_CELL_TAG),
                )
            }
        }

        onNodeWithTag(EPISODE_CELL_TAG).performClick()
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(0, longClickCount)
        }

        onNodeWithTag(EPISODE_CELL_TAG).performTouchInput { longClick() }
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(1, longClickCount)
        }
    }

    @Test
    fun `episode grid cell with still shows the image keeps text and handles clicks`() = runWynimeComposeUiTest {
        var clickCount = 0
        var longClickCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = TestEpisodeStillUrl),
                    isPlaying = false,
                    onClick = { clickCount++ },
                    onLongClick = { longClickCount++ },
                    modifier = Modifier.testTag(EPISODE_CELL_TAG).width(104.dp),
                    height = 64.dp,
                )
            }
        }

        onNodeWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertExists()
        onNodeWithText("13", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("第三话", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(EPISODE_CELL_TAG).performClick()
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(0, longClickCount)
        }
        onNodeWithTag(EPISODE_CELL_TAG).performTouchInput { longClick() }
        runOnIdle {
            assertEquals(1, clickCount)
            assertEquals(1, longClickCount)
        }
    }

    @Test
    fun `episode grid cell without still has no image layer`() = runWynimeComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = null),
                    isPlaying = false,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.testTag(EPISODE_CELL_TAG).width(104.dp),
                    height = 64.dp,
                )
            }
        }

        onNodeWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("第三话", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `episode grid cell hides the still when images are disabled`() = runWynimeComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = TestEpisodeStillUrl),
                    isPlaying = true,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.testTag(EPISODE_CELL_TAG).width(104.dp),
                    height = 64.dp,
                    showImage = false,
                )
            }
        }

        onNodeWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("13", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `done cell shows the watched badge and badge click toggles without cell click`() = runWynimeComposeUiTest {
        var clickCount = 0
        var toggleCount = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = TestEpisodeStillUrl, collectionType = UnifiedCollectionType.DONE, playProgress = 0.5f),
                    isPlaying = false,
                    onClick = { clickCount++ },
                    onLongClick = { toggleCount++ },
                    modifier = Modifier.testTag(EPISODE_CELL_TAG).width(128.dp),
                    height = 72.dp,
                )
            }
        }

        onNodeWithTag(EPISODE_PROGRESS_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(EPISODE_WATCHED_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed().performClick()
        runOnIdle {
            assertEquals(0, clickCount)
            assertEquals(1, toggleCount)
        }
    }

    @Test
    fun `unfinished cell with play record shows progress bar without badge`() = runWynimeComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = null, playProgress = 0.4f),
                    isPlaying = false,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.width(128.dp),
                    height = 72.dp,
                )
            }
        }

        onNodeWithTag(EPISODE_PROGRESS_TAG, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(EPISODE_WATCHED_BADGE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `never played cell shows neither badge nor progress bar`() = runWynimeComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeGridCell(
                    item = stillItem(imageMedium = TestEpisodeStillUrl),
                    isPlaying = false,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier.width(128.dp),
                    height = 72.dp,
                )
            }
        }

        onNodeWithTag(EPISODE_PROGRESS_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(EPISODE_WATCHED_BADGE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `episodes row renders cells with and without stills`() = runWynimeComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodesRow(
                    episodes = listOf(
                        stillItem(imageMedium = TestEpisodeStillUrl, episodeId = 1, sort = 1),
                        stillItem(imageMedium = null, episodeId = 2, sort = 2),
                    ),
                    currentEpisodeId = 1,
                    onEpisodeClick = {},
                    onEpisodeLongClick = {},
                    modifier = Modifier.testTag(EPISODES_ROW_TAG),
                )
            }
        }

        onNodeWithTag(EPISODES_ROW_TAG).assertExists()
        onAllNodesWithTag(EPISODE_STILL_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    private fun stillItem(
        imageMedium: String?,
        episodeId: Int = 13,
        sort: Int = 13,
        collectionType: UnifiedCollectionType = UnifiedCollectionType.WISH,
        playProgress: Float? = null,
    ): EpisodeListItem = createTestEpisodeListItem(
        sort = EpisodeSort(sort),
        episodeId = episodeId,
        nameCn = "第三话",
        collectionType = collectionType,
        isBroadcast = true,
        imageMedium = imageMedium,
        playProgress = playProgress,
    )
}

private const val EPISODE_CELL_TAG = "episode_grid_cell"
private const val EPISODES_ROW_TAG = "episodes_row"
