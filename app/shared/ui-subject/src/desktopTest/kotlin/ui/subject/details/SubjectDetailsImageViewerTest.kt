package com.wynime.app.ui.subject.details

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import com.wynime.app.data.models.subject.TestCoverImage
import com.wynime.app.data.models.subject.TestSelfRatingInfo
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.data.models.subject.TestSubjectInfo
import com.wynime.app.ui.comment.createTestCommentState
import com.wynime.app.ui.foundation.IMAGE_VIEWER_TEST_TAG
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runOnSwingEdt
import com.wynime.app.ui.search.createTestPager
import com.wynime.app.ui.subject.details.components.SUBJECT_COVER_IMAGE_TEST_TAG
import com.wynime.app.ui.subject.details.state.SubjectDetailsUiState
import com.wynime.app.ui.subject.details.state.SubjectDetailsState
import com.wynime.app.ui.subject.episode.list.TestEpisodeListUiState
import com.wynime.app.ui.user.TestSelfInfoUiState
import com.wynime.app.data.models.subject.TestSubjectProgressInfos
import com.wynime.app.ui.subject.TestSubjectAiringInfo
import com.wynime.app.ui.rating.TestEditableRatingUiState
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SubjectDetailsImageViewerTest {
    private val outDir: File =
        File(System.getProperty("wynime.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private fun SkikoComposeUiTest.capture(name: String) {
        val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?.bytes
            ?: error("Failed to encode screenshot $name")
        File(outDir, "$name.png").writeBytes(png)
    }

    private fun testStateWithImages(scope: CoroutineScope): SubjectDetailsState {
        val subjectInfo = TestSubjectCollections.first().subjectInfo
        val characters = TestSubjectCharacterList
        val info = TestSubjectInfo.copy(imageLarge = TestCoverImage)
        return SubjectDetailsState(
            subjectId = info.subjectId,
            info = info,
            charactersPager = createTestPager(characters),
            exposedCharactersPager = createTestPager(characters),
            staffPager = createTestPager(TestSubjectStaffInfo),
            exposedStaffPager = createTestPager(TestSubjectStaffInfo),
            relatedSubjectsPager = createTestPager(TestRelatedSubjects),
            subjectCommentState = createTestCommentState(scope),
            uiState = MutableStateFlow(
                SubjectDetailsUiState(
                    subjectId = info.subjectId,
                    displayName = info.displayName,
                    selfCollectionType = UnifiedCollectionType.DOING,
                    airingInfo = TestSubjectAiringInfo,
                    progressInfo = TestSubjectProgressInfos.ContinueWatching2,
                    episodeListUiState = TestEpisodeListUiState,
                    totalStaffCount = TestSubjectStaffInfo.size,
                    totalCharactersCount = characters.size,
                    collectionTypeEdit = EditableSubjectCollectionTypeState.Presentation.Placeholder.copy(
                        selfCollectionType = UnifiedCollectionType.DOING,
                        isPlaceholder = false,
                    ),
                    rating = TestEditableRatingUiState,
                    isPlaceholder = false,
                ),
            ),
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun runPageTest(widthDp: Int, heightDp: Int, block: SkikoComposeUiTest.() -> Unit) = runOnSwingEdt {

        Dispatchers.resetMain()

        runSkikoComposeUiTest(Size(widthDp.toFloat(), heightDp.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        val scope = rememberCoroutineScope()
                        val state = remember {
                            testStateWithImages(scope).let { SubjectDetailsLoadState.Ok(it.subjectId, it) }
                        }
                        SubjectDetailsScreen(
                            state,
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
            block()
        }
    }

    @Test
    fun `compact - click cover opens image viewer`() = runPageTest(360, 800) {
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(SUBJECT_COVER_IMAGE_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertIsDisplayed()
        capture("image-viewer-compact-cover-open")
    }

    @Test
    fun `multi column - click cover opens image viewer, tap closes it`() = runPageTest(1400, 1400) {
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertDoesNotExist()
        capture("image-viewer-multicolumn-before")
        onNodeWithTag(SUBJECT_COVER_IMAGE_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertIsDisplayed()
        capture("image-viewer-multicolumn-cover-open")

        onNodeWithTag(IMAGE_VIEWER_TEST_TAG).performTouchInput {
            down(center)
            up()
        }
        waitUntil(timeoutMillis = 5000) {
            onAllNodesWithTag(IMAGE_VIEWER_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }
    }
}
