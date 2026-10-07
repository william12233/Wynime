package com.wynime.app.ui.foundation

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.wynime.app.ui.framework.doesNotExist
import com.wynime.app.ui.framework.exists
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test

private const val TAG_SCROLL_CONTROL_LEFT_BUTTON = "scrollControlLeftButton"
private const val TAG_SCROLL_CONTROL_RIGHT_BUTTON = "scrollControlRightButton"
private const val TAG_LAZY_LIST = "lazyList"

class HorizontalScrollControlScaffoldTest {
    private val SemanticsNodeInteractionsProvider.scrollControlLeftButton
        get() = onNodeWithTag(TAG_SCROLL_CONTROL_LEFT_BUTTON, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.scrollControlRightButton
        get() = onNodeWithTag(TAG_SCROLL_CONTROL_RIGHT_BUTTON, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.lazyList
        get() = onNodeWithTag(TAG_LAZY_LIST, useUnmergedTree = true)

    @Composable
    private fun View(
        listState: LazyListState = rememberLazyListState(),
        itemCount: Int,
    ) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current

        HorizontalScrollControlScaffold(
            rememberHorizontalScrollControlState(
                scrollableState = listState,
                onClickScroll = { direction ->
                    scope.launch {
                        listState.scrollBy(
                            with(density) {
                                HorizontalScrollControlDefaults.ScrollStep.toPx() *
                                        (if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1)
                            },
                        )
                    }
                },
            ),
            modifier = Modifier.width(500.dp),
            scrollLeftButton = {
                HorizontalScrollControlDefaults.ScrollLeftButton(
                    Modifier.testTag(TAG_SCROLL_CONTROL_LEFT_BUTTON),
                )
            },
            scrollRightButton = {
                HorizontalScrollControlDefaults.ScrollRightButton(
                    Modifier.testTag(TAG_SCROLL_CONTROL_RIGHT_BUTTON),
                )
            },
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.testTag(TAG_LAZY_LIST).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(itemCount) {
                    Surface(
                        color = MaterialTheme.colors.secondary,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Spacer(Modifier.size(120.dp, 200.dp))
                    }
                }
            }
        }
    }

    @Test
    fun `too many items - left button - invisible when cant scroll forward`() = runWynimeComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.exists() }
        }
    }

    @Test
    fun `too many items - left button - visible when can scroll forward`() = runWynimeComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                swipeLeft(centerX, centerX - 100)
            }
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.exists() }
            waitUntil { scrollControlRightButton.exists() }
        }
    }

    @Test
    fun `too many items - right button - invisible when already at the right end`() = runWynimeComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                repeat(10) { swipeLeft() }
            }
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.exists() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }
    }

    @Test
    fun `too many items - right button - visible when can scroll forward`() = runWynimeComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, 10)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                repeat(10) { swipeLeft() }
                swipeRight(centerX, centerX + 100)
            }
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.exists() }
            waitUntil { scrollControlRightButton.exists() }
        }
    }

    @Test
    fun `too less items - both buttons - composite test`() = runWynimeComposeUiTest {
        val listState = LazyListState()

        setContent {
            View(listState, itemCount = 2)
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }

            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                swipeLeft(centerX, centerX - 100)
            }
        }

        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerLeft + Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }

            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }
        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }

            waitUntil { scrollControlRightButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                repeat(10) { swipeLeft() }
            }
        }
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlLeftButton.doesNotExist() }
        }

        runOnIdle {
            lazyList.performTouchInput {
                swipeRight(centerX, centerX + 100)
            }
        }
        runOnIdle {
            lazyList.performMouseInput {
                moveTo(centerRight - Offset(10f, 0f))
            }
        }

        runOnIdle {
            waitUntil { scrollControlLeftButton.doesNotExist() }
            waitUntil { scrollControlRightButton.doesNotExist() }
        }
    }
}