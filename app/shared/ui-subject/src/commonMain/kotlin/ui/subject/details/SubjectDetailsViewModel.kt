package com.wynime.app.ui.subject.details

import androidx.compose.runtime.Stable
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.domain.episode.SetEpisodeCollectionTypeUseCase
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.rating.RateRequest
import com.wynime.app.ui.subject.details.state.SubjectDetailsStateFactory
import com.wynime.app.ui.subject.details.state.SubjectDetailsStateLoader
import com.wynime.app.ui.user.SelfInfoStateProducer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
open class SubjectDetailsViewModel(
    private val subjectId: Int,
    private val placeholder: SubjectInfo? = null
) : AbstractViewModel(), KoinComponent {
    private val factory: SubjectDetailsStateFactory by inject()
    val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase by inject()

    private val stateLoader = SubjectDetailsStateLoader(factory, backgroundScope)

    val state get() = stateLoader.state
    val authState = SelfInfoStateProducer(koin = getKoin()).flow

    fun load() {
        stateLoader.load(subjectId, placeholder)
    }

    fun reload() {
        stateLoader.load(subjectId, placeholder, force = true)
    }
}

suspend inline fun SubjectCollectionRepository.updateRating(subjectId: Int, request: RateRequest) {
    return this.updateRating(subjectId, request.score, request.comment, isPrivate = request.isPrivate)
}
