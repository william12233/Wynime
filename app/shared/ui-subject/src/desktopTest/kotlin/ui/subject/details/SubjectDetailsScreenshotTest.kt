package com.wynime.app.ui.subject.details

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.models.subject.TestSelfRatingInfo
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.data.models.subject.TestSubjectInfo
import com.wynime.app.ui.comment.createTestCommentState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.search.createTestPager
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
class SubjectDetailsScreenshotTest {
    private val outDir: File =
        File(System.getProperty("wynime.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private fun richTestState(scope: CoroutineScope): SubjectDetailsState {
        val subjectInfo = TestSubjectCollections.first().subjectInfo
        return SubjectDetailsState(
            subjectId = TestSubjectInfo.subjectId,
            info = TestSubjectInfo,
            charactersPager = createTestPager(TestSubjectCharacterList),
            exposedCharactersPager = createTestPager(TestSubjectCharacterList.take(8)),
            staffPager = createTestPager(TestSubjectStaffInfo),
            exposedStaffPager = createTestPager(TestSubjectStaffInfo.take(10)),
            relatedSubjectsPager = createTestPager(TestRelatedSubjects),
            subjectCommentState = createTestCommentState(scope),
            uiState = MutableStateFlow(
                SubjectDetailsUiState(
                    subjectId = TestSubjectInfo.subjectId,
                    displayName = TestSubjectInfo.displayName,
                    selfCollectionType = UnifiedCollectionType.DOING,
                    airingInfo = TestSubjectAiringInfo,
                    progressInfo = TestSubjectProgressInfos.ContinueWatching2,
                    episodeListUiState = TestEpisodeListUiState,
                    totalStaffCount = TestSubjectStaffInfo.size,
                    totalCharactersCount = TestSubjectCharacterList.size,
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

    private fun capture(widthDp: Int, heightDp: Int, name: String, scrolled: Boolean = false) {
        runSkikoComposeUiTest(Size(widthDp.toFloat(), heightDp.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        val scope = rememberCoroutineScope()
                        val state = remember {
                            richTestState(scope).let { SubjectDetailsLoadState.Ok(it.subjectId, it) }
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
            if (scrolled) {

                onRoot().performTouchInput { swipeUp(durationMillis = 1500) }
                waitForIdle()
            }
            val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
                ?: error("Failed to encode screenshot $name")
            File(outDir, "$name.png").writeBytes(png)
        }
    }

    @Test
    fun compact() = capture(360, 1728, "detail-compact-360")

    @Test
    fun mediumNarrow() = capture(700, 1400, "detail-medium-narrow-700")

    @Test
    fun medium() = capture(1400, 1464, "detail-medium-1400")

    @Test
    fun expanded() = capture(1600, 1213, "detail-expanded-1600")

    @Test
    fun expandedScrolled() = capture(1600, 800, "detail-expanded-1600-scrolled", scrolled = true)
}
