package com.wynime.app.ui.rating

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.data.models.subject.TestSelfRatingInfo
import com.wynime.app.data.models.subject.TestSubjectInfo
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.rating_requires_collection
import com.wynime.app.ui.lang.settings_mediasource_close
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Immutable
data class EditableRatingUiState(
    val ratingInfo: RatingInfo,
    val selfRatingInfo: SelfRatingInfo,

    val enableEdit: Boolean,
    val showRatingDialog: Boolean = false,

    val showRatingRequiresCollectionDialog: Boolean = false,
    val isUpdating: Boolean = false,
) {
    companion object {
        val Placeholder = EditableRatingUiState(
            ratingInfo = RatingInfo.Empty,
            selfRatingInfo = SelfRatingInfo.Empty,
            enableEdit = false,
        )
    }
}

interface EditableRatingActions {
    fun requestEditRating()
    fun cancelEditRating()
    fun submitRating(request: RateRequest)

    suspend fun updateScore(score: Int): LoadError?
    fun dismissRatingRequiresCollectionDialog()

    companion object Noop : EditableRatingActions {
        override fun requestEditRating() {}
        override fun cancelEditRating() {}
        override fun submitRating(request: RateRequest) {}
        override suspend fun updateScore(score: Int): LoadError? = null
        override fun dismissRatingRequiresCollectionDialog() {}
    }
}

@Composable
fun EditableRating(
    uiState: EditableRatingUiState,
    actions: EditableRatingActions,
    modifier: Modifier = Modifier,
) {
    EditableRatingDialogsHost(uiState, actions)
    Rating(
        rating = uiState.ratingInfo,
        selfRatingScore = uiState.selfRatingInfo.score,
        onClick = { actions.requestEditRating() },
        clickEnabled = uiState.enableEdit && !uiState.isUpdating,
        modifier = modifier,
    )
}

@Composable
fun EditableRatingDialogsHost(
    uiState: EditableRatingUiState,
    actions: EditableRatingActions,
) {
    if (uiState.showRatingRequiresCollectionDialog) {
        AlertDialog(
            { actions.dismissRatingRequiresCollectionDialog() },
            text = { Text(stringResource(Lang.rating_requires_collection)) },
            confirmButton = {
                TextButton({ actions.dismissRatingRequiresCollectionDialog() }) {
                    Text(stringResource(Lang.settings_mediasource_close))
                }
            },
        )
    }

    if (uiState.showRatingDialog) {
        val selfRatingInfo = uiState.selfRatingInfo
        RatingEditorDialog(
            remember(selfRatingInfo) {
                RatingEditorState(
                    initialScore = selfRatingInfo.score,
                    initialComment = selfRatingInfo.comment ?: "",
                    initialIsPrivate = selfRatingInfo.isPrivate,
                )
            },
            onDismissRequest = { actions.cancelEditRating() },
            onRate = { actions.submitRating(it) },
            isLoading = uiState.isUpdating,
        )
    }
}

@TestOnly
val TestEditableRatingUiState
    get() = EditableRatingUiState(
        ratingInfo = TestSubjectInfo.ratingInfo,
        selfRatingInfo = TestSelfRatingInfo,
        enableEdit = true,
    )
