package com.wynime.app.domain.mediasource.instance

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wynime.app.domain.media.fetch.MediaSourceInfoWithId
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.usecase.UseCase

interface GetMediaSourceInstancesUseCase : UseCase {
    operator fun invoke(): Flow<List<MediaSourceInstance>>

    fun getAsMediaSourceInfoWithId(): Flow<List<MediaSourceInfoWithId>> = invoke().map { instances ->
        instances.map {
            MediaSourceInfoWithId(
                instanceId = it.instanceId,
                mediaSourceId = it.mediaSourceId,
                info = it.source.info,
            )
        }
    }
}

class GetMediaSourceInstancesUseCaseImpl(
    private val mediaSourceManager: MediaSourceManager,
) : GetMediaSourceInstancesUseCase {
    override fun invoke(): Flow<List<MediaSourceInstance>> = mediaSourceManager.allInstances
}
