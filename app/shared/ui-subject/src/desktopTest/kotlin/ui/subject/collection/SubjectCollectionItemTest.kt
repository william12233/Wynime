package com.wynime.app.ui.subject.collection

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.app.ui.subject.collection.components.createTestEditableSubjectCollectionTypeState
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test

@OptIn(TestOnly::class)
class SubjectCollectionItemTest {
    @Test
    fun `edit collection menu stays open when action state is replaced`() = runWynimeComposeUiTest {
        val recompositionTrigger = mutableIntStateOf(0)

        setContent {
            ProvideCompositionLocalsForPreview {
                val collection = TestSubjectCollections.first()
                val collectionType = remember { MutableStateFlow(collection.collectionType) }
                val backgroundScope = rememberCoroutineScope()
                recompositionTrigger.intValue
                val editableState = createTestEditableSubjectCollectionTypeState(
                    collectionType,
                    backgroundScope,
                )

                SubjectCollectionItem(
                    item = collection,
                    editableSubjectCollectionTypeState = editableState,
                    onClick = {},
                    onShowEpisodeList = {},
                    playButton = {},
                )
            }
        }

        onNodeWithTag(SubjectCollectionItemTestTags.MoreButton).performClick()
        onNodeWithTag(SubjectCollectionItemTestTags.EditCollectionTypeMenu).assertIsDisplayed()

        runOnIdle { recompositionTrigger.intValue++ }

        onNodeWithTag(SubjectCollectionItemTestTags.EditCollectionTypeMenu).assertIsDisplayed()
    }
}
