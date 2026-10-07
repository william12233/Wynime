package com.wynime.app.ui.download.subject

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import com.wynime.app.ui.foundation.AbstractViewModel

class SubjectDownloadsViewModel(
    subjectId: Int,
    presenters: SubjectDownloadsPresenterFactory,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(coroutineContext) {
    val presenter: SubjectDownloadsPresenter = presenters.create(subjectId, backgroundScope)
}
