package com.wynime.app.domain.mediasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext

fun interface GetMediaSelectorSourceTiersUseCase : UseCase {
    operator fun invoke(): Flow<MediaSelectorSourceTiers>
}

class GetMediaSelectorSourceTiersUseCaseImpl(
    private val mediaSourceManager: MediaSourceManager,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : GetMediaSelectorSourceTiersUseCase {
    override fun invoke(): Flow<MediaSelectorSourceTiers> = mediaSourceManager.mediaSourceTiersFlow().flowOn(dispatcher)
}
