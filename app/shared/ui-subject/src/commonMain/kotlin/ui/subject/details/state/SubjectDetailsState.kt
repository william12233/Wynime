package com.wynime.app.ui.subject.details.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.wynime.app.data.models.subject.RelatedCharacterInfo
import com.wynime.app.data.models.subject.RelatedPersonInfo
import com.wynime.app.data.models.subject.RelatedSubjectInfo
import com.wynime.app.data.models.subject.SubjectAiringInfo
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.ui.comment.CommentReportState
import com.wynime.app.ui.comment.CommentState
import com.wynime.app.ui.rating.EditableRatingActions
import com.wynime.app.ui.rating.EditableRatingUiState
import com.wynime.app.ui.subject.AiringLabelState
import com.wynime.app.ui.subject.SubjectProgressState
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.app.ui.subject.collection.components.SubjectCollectionTypeEditActions
import com.wynime.app.ui.subject.episode.list.EpisodeListUiState
import com.wynime.datasources.api.topic.UnifiedCollectionType

interface SubjectDetailsActions : SubjectCollectionTypeEditActions, EditableRatingActions {
    companion object Noop : SubjectDetailsActions,
        SubjectCollectionTypeEditActions by SubjectCollectionTypeEditActions.Noop,
        EditableRatingActions by EditableRatingActions.Noop
}

@Stable
class SubjectDetailsState(
    val subjectId: Int,
    val info: SubjectInfo?,

    val staffPager: Flow<PagingData<RelatedPersonInfo>>,
    val exposedStaffPager: Flow<PagingData<RelatedPersonInfo>>,
    val charactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val exposedCharactersPager: Flow<PagingData<RelatedCharacterInfo>>,
    val relatedSubjectsPager: Flow<PagingData<RelatedSubjectInfo>>,
    val subjectCommentState: CommentState,

    val uiState: StateFlow<SubjectDetailsUiState>,
    val subjectCommentReportState: CommentReportState? = null,
    actions: SubjectDetailsActions = SubjectDetailsActions.Noop,
) : SubjectDetailsActions by actions {
    val detailsTabLazyListState = LazyListState()
    val commentTabLazyGridState = LazyGridState()
}

@Immutable
data class SubjectDetailsUiState(
    val subjectId: Int,
    val displayName: String,
    val selfCollectionType: UnifiedCollectionType,

    val airingInfo: SubjectAiringInfo?,

    val progressInfo: SubjectProgressInfo?,
    val episodeListUiState: EpisodeListUiState,

    val totalStaffCount: Int?,

    val totalCharactersCount: Int?,

    val collectionTypeEdit: EditableSubjectCollectionTypeState.Presentation,

    val rating: EditableRatingUiState,
    val isPlaceholder: Boolean = false,
) {
    val selfCollected: Boolean get() = selfCollectionType != UnifiedCollectionType.NOT_COLLECTED

    companion object {
        val Placeholder = SubjectDetailsUiState(
            subjectId = 0,
            displayName = "",
            selfCollectionType = UnifiedCollectionType.NOT_COLLECTED,
            airingInfo = null,
            progressInfo = null,
            episodeListUiState = EpisodeListUiState.Placeholder,
            totalStaffCount = null,
            totalCharactersCount = null,
            collectionTypeEdit = EditableSubjectCollectionTypeState.Presentation.Placeholder,
            rating = EditableRatingUiState.Placeholder,
            isPlaceholder = true,
        )
    }
}

@Composable
fun SubjectDetailsUiState.rememberAiringLabelState(): AiringLabelState {
    val airingInfo = airingInfo
    val progressInfo = progressInfo
    return remember(airingInfo, progressInfo) { AiringLabelState(airingInfo, progressInfo) }
}

@Composable
fun SubjectDetailsUiState.rememberSubjectProgressState(): SubjectProgressState {
    val progressInfo = progressInfo
    return remember(progressInfo) { SubjectProgressState(progressInfo) }
}
