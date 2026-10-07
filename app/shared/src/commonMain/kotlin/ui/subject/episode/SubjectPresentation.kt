package com.wynime.app.ui.subject.episode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.nameOrNameCn

@Immutable
class SubjectPresentation(
    val title: String,
    val isPlaceholder: Boolean = false,
    val info: SubjectInfo,

    val originalTitle: String = title,
) {
    companion object {
        @Stable
        val Placeholder = SubjectPresentation(
            title = "placeholder",
            isPlaceholder = true,
            info = SubjectInfo.Empty,
        )
    }
}

fun SubjectInfo.toPresentation(): SubjectPresentation {
    return SubjectPresentation(
        title = displayName,
        info = this,
        originalTitle = nameOrNameCn,
    )
}
