package com.wynime.app.ui.foundation.animation

import androidx.compose.runtime.Stable

@Stable
object SharedTransitionKeys {
    @Stable
    fun subjectCoverImage(subjectId: Int) = "subject-cover-$subjectId"

    @Stable
    fun subjectTitle(subjectId: Int) = "subject-title-$subjectId"

    @Stable
    fun subjectBounds(subjectId: Int) = "subject-bounds-$subjectId"
}
