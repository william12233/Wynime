package com.wynime.app.ui.subject.details.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.models.subject.TestSelfRatingInfo
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.data.models.subject.TestSubjectInfo
import com.wynime.app.ui.comment.createTestCommentState
import com.wynime.app.ui.search.createTestPager
import com.wynime.app.ui.subject.details.TestRelatedSubjects
import com.wynime.app.ui.subject.details.TestSubjectCharacterList
import com.wynime.app.data.models.subject.TestSubjectProgressInfos
import com.wynime.app.ui.subject.TestSubjectAiringInfo
import com.wynime.app.ui.subject.episode.list.EpisodeListUiState
import com.wynime.app.ui.rating.TestEditableRatingUiState
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

@TestOnly
fun createTestSubjectDetailsState(
    backgroundScope: CoroutineScope,
): SubjectDetailsState {
    val subjectCollectionInfo = TestSubjectCollections.first()
    val subjectInfo = subjectCollectionInfo.subjectInfo
    return SubjectDetailsState(
        subjectId = TestSubjectInfo.subjectId,
        info = TestSubjectInfo,
        charactersPager = createTestPager(TestSubjectCharacterList),
        exposedCharactersPager = createTestPager(TestSubjectCharacterList.take(6)),
        staffPager = createTestPager(emptyList()),
        exposedStaffPager = createTestPager(emptyList()),
        relatedSubjectsPager = createTestPager(TestRelatedSubjects),
        subjectCommentState = createTestCommentState(backgroundScope),
        uiState = MutableStateFlow(
            SubjectDetailsUiState(
                subjectId = TestSubjectInfo.subjectId,
                displayName = TestSubjectInfo.displayName,
                selfCollectionType = UnifiedCollectionType.WISH,
                airingInfo = TestSubjectAiringInfo,
                progressInfo = TestSubjectProgressInfos.ContinueWatching2,
                episodeListUiState = EpisodeListUiState.Placeholder,
                totalStaffCount = 0,
                totalCharactersCount = TestSubjectCharacterList.size,
                collectionTypeEdit = EditableSubjectCollectionTypeState.Presentation.Placeholder.copy(
                    selfCollectionType = UnifiedCollectionType.WISH,
                    isPlaceholder = false,
                ),
                rating = TestEditableRatingUiState,
            ),
        ),
    )
}
