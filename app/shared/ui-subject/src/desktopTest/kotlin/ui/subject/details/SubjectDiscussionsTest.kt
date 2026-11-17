package com.wynime.app.ui.subject.details

import androidx.paging.PagingData
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.resetMain
import com.wynime.app.data.models.UserInfo
import com.wynime.app.data.models.subject.SubjectDiscussion
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.TestGlobalLifecycleOwner
import com.wynime.app.ui.subject.details.state.createTestSubjectDetailsState
import com.wynime.app.ui.user.TestSelfInfoUiState
import com.wynime.utils.platform.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class, ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class SubjectDiscussionsTest {
    @Test
    fun `compact details open the discussion tab`() = verifyDiscussions(360, 800)

    @Test
    fun `wide details open a dialog with the same discussions`() = verifyDiscussions(1400, 1050)

    private fun verifyDiscussions(width: Int, height: Int) {
        Dispatchers.resetMain()
        val collectionCount = AtomicInteger()
        val detail = createTestSubjectDetailsState(flow {
            collectionCount.incrementAndGet()
            emit(PagingData.from(listOf(Topic)))
        })
        val openedUrls = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUrls += uri
            }
        }

        runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    CompositionLocalProvider(
                        LocalUriHandler provides uriHandler,
                        LocalLifecycleOwner provides TestGlobalLifecycleOwner,
                    ) {
                        SubjectDetailsScreen(
                            SubjectDetailsLoadState.Ok(detail.subjectId, detail),
                            TestSelfInfoUiState,
                            onPlay = {},
                            onLoadErrorRetry = {},
                            onClickTag = {},
                            onEpisodeCollectionUpdate = {},
                        )
                    }
                }
            }
            waitForIdle()
            assertEquals(0, collectionCount.get())
            if (width > 600) onNodeWithText("Discussions").performScrollTo()
            onNodeWithText("Discussions").performClick()

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("A Bangumi user").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("A Bangumi user").assertIsDisplayed()
            onNodeWithText("The opening post").assertIsDisplayed()
            onNodeWithText("A topic about the show").performClick()
            assertEquals(listOf(Topic.originalUrl), openedUrls)
        }
    }

    private companion object {
        val Topic = SubjectDiscussion(
            id = 29838,
            title = "A topic about the show",
            author = UserInfo(id = "1", username = "bgm-user", nickname = "A Bangumi user"),
            createdAtMillis = 1716093004000,
            content = "[b]The opening post[/b]",
        )
    }
}
