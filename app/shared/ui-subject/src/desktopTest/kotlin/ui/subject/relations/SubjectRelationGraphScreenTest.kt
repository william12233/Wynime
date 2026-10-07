package com.wynime.app.ui.subject.relations

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.subject.SubjectRelationGraph
import com.wynime.app.data.models.subject.SubjectRelationGraphSubject
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.WynimeComposeUiTest
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.utils.platform.annotations.TestOnly
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SubjectRelationGraphScreenTest {
    private val clicked = mutableListOf<SubjectRelationGraphSubject>()
    private val originalLocale = Locale.getDefault()

    @BeforeTest
    fun setLocale() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(originalLocale)
    private var retryCount = 0

    private fun WynimeComposeUiTest.setContent(state: SubjectRelationGraphUiState, width: Dp = 390.dp) {
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectRelationGraphScreen(
                    state,
                    onRetry = { retryCount++ },
                    onClickSubject = { clicked.add(it) },
                    Modifier.width(width),
                )
            }
        }
    }

    private fun WynimeComposeUiTest.setContent(graph: SubjectRelationGraph, width: Dp = 390.dp) =
        setContent(SubjectRelationGraphUiState(graph, error = null), width)

    @Test
    fun `compact - shows series summary and marks the current subject`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.ReZero)
        onNodeWithText("7 in main story · 8 related").assertExists()
        onNodeWithText("Part 2 · Current").assertExists()
        onNodeWithText("Part 1").assertExists()
        onAllNodesWithText("Part 1 · Current").assertCountEquals(0)
    }

    @Test
    fun `compact - clicking a main node or a branch opens that subject`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.ReZero)
        onNodeWithText("Re：从零开始的休息时间2").performClick()

        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performScrollToIndex(3)
        onNodeWithText("Re：从零开始的异世界生活 第二季 后半部分").performClick()
        assertEquals(listOf(310194, 316247), clicked.map { it.subjectId })
    }

    @Test
    fun `compact - branches beyond three are collapsed until expanded`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.manyBranches(5))
        onNodeWithText("番外 3").assertExists()
        onNodeWithText("番外 4").assertDoesNotExist()
        onNodeWithText("2 more").performClick()
        onNodeWithText("番外 5").assertExists()
        onNodeWithText("Show less").performClick()
        onNodeWithText("番外 4").assertDoesNotExist()
        onNodeWithText("This series is too large. Only part of it is shown.").assertExists()
    }

    @Test
    fun `compact - collapsed part is expanded when it contains the current subject`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.manyBranches(5).copy(subjectId = 105))
        onNodeWithText("番外 5").assertExists()
        onNodeWithText("Show less").assertExists()
    }

    @Test
    fun `wide - branches beyond four are collapsed until expanded`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.manyBranches(5), width = 1280.dp)
        onNodeWithText("番外 4").assertExists()
        onNodeWithText("番外 5").assertDoesNotExist()

        onNodeWithText("1 more").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithText("番外 5").assertExists()
        onNodeWithText("第二季").performScrollTo().performClick()
        assertEquals(listOf(2), clicked.map { it.subjectId })
    }

    @Test
    fun `series name is removed from branch names`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.ReZero)
        onNodeWithText("雪之回忆").assertExists()

        onNodeWithText("Re：从零开始的休息时间").assertExists()
    }

    @Test
    fun `compact - long series opens at the current subject`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.Kimetsu)
        onNodeWithText("Part 2 · Current").assertExists()

        onNodeWithText("鬼灭之刃 无限列车篇").assertExists()
        onNodeWithText("兄妹的羁绊").assertDoesNotExist()
    }

    @Test
    fun `wide - vertical mouse wheel scrolls the timeline horizontally`() = runWynimeComposeUiTest {

        setContent(TestSubjectRelationGraphs.Kimetsu.copy(subjectId = 441939), width = 1280.dp)
        onNodeWithText("兄妹的羁绊").assertIsNotDisplayed()
        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performMouseInput {
            moveTo(center)
            repeat(60) { scroll(-3f) }
        }
        onNodeWithText("兄妹的羁绊").assertIsDisplayed()
    }

    @Test
    fun `movies on the mainline have no ordinal and compilations are listed under seasons`() = runWynimeComposeUiTest {
        setContent(TestSubjectRelationGraphs.Kimetsu)
        onNodeWithTag(SUBJECT_RELATION_GRAPH_TEST_TAG).performScrollToIndex(0)
        onNodeWithText("Part 1").assertExists()

        onNodeWithText("兄妹的羁绊").assertExists()
        onNodeWithText("Compilation · 2019").assertExists()

        onNodeWithText("4 in main story", substring = true).assertDoesNotExist()
        onNodeWithText("9 in main story · 10 related").assertExists()
        onNodeWithText("Special").assertExists()
    }

    @Test
    fun `error can be retried`() = runWynimeComposeUiTest {
        setContent(SubjectRelationGraphUiState(graph = null, error = LoadError.NetworkError))
        onNodeWithText("Retry").performClick()
        assertEquals(1, retryCount)
    }
}
