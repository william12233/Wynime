package com.wynime.app.ui.rating

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.tools.MonoTasker
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsEvent.Companion.RatingEnter
import com.wynime.utils.analytics.AnalyticsEvent.Companion.RatingSubmit
import com.wynime.utils.analytics.recordEvent

class RatingEditController(
    private val isCollected: () -> Boolean,
    private val currentSelfRating: () -> SelfRatingInfo,
    private val onRate: suspend (RateRequest) -> Unit,
    backgroundScope: CoroutineScope,
    private val subjectId: Int? = null,
) : EditableRatingActions {
    private val showRatingDialog = MutableStateFlow(false)
    private val showRatingRequiresCollectionDialog = MutableStateFlow(false)
    private val tasker = MonoTasker(backgroundScope)

    fun uiStateFlow(
        ratingInfo: RatingInfo,
        selfRatingInfo: Flow<SelfRatingInfo>,
        enableEdit: Flow<Boolean>,
    ): Flow<EditableRatingUiState> = combine(
        selfRatingInfo,
        enableEdit,
        showRatingDialog,
        showRatingRequiresCollectionDialog,
        tasker.isRunning,
    ) { self, enable, showDialog, showRequiresCollection, updating ->
        EditableRatingUiState(
            ratingInfo = ratingInfo,
            selfRatingInfo = self,
            enableEdit = enable,
            showRatingDialog = showDialog,
            showRatingRequiresCollectionDialog = showRequiresCollection,
            isUpdating = updating,
        )
    }

    override fun requestEditRating() {
        if (isCollected()) {
            showRatingDialog.value = true
            val hasExistingScore = currentSelfRating().score > 0
            Analytics.recordEvent(RatingEnter) {
                put("has_existing_score", hasExistingScore)
                subjectId?.let { put("subject_id", it) }
            }
        } else {
            showRatingRequiresCollectionDialog.value = true
        }
    }

    override fun cancelEditRating() {
        showRatingDialog.value = false
        showRatingRequiresCollectionDialog.value = false
    }

    override fun submitRating(request: RateRequest) {
        tasker.launch {
            onRate(request)
            Analytics.recordEvent(RatingSubmit) {
                put("score", request.score)
                put("has_comment", request.comment.isNotEmpty())
                put("comment_length", request.comment.length)
                put("is_private", request.isPrivate)
                subjectId?.let { put("subject_id", it) }
            }
            showRatingDialog.value = false
        }
    }

    override suspend fun updateScore(score: Int): LoadError? {
        require(score in 0..10)
        check(isCollected())
        val current = currentSelfRating()
        return tasker.async {
            LoadError.runAndWrapOrThrowCancellation {
                onRate(RateRequest(score, current.comment.orEmpty(), current.isPrivate))
                Analytics.recordEvent(RatingSubmit) {
                    put("score", score)
                    subjectId?.let { put("subject_id", it) }
                }
            }
        }.await()
    }

    override fun dismissRatingRequiresCollectionDialog() {
        showRatingRequiresCollectionDialog.value = false
    }
}
