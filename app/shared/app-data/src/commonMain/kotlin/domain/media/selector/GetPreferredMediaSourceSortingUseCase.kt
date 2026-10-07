package com.wynime.app.domain.media.selector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.wynime.app.domain.media.fetch.MediaSourceFetchResult
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext

fun interface GetPreferredMediaSourceSortingUseCase : UseCase {

    operator fun invoke(): Flow<List<String>>
}

class GetPreferredMediaSourceSortingUseCaseImpl(
    private val mediaSourceManager: MediaSourceManager,
    private val context: CoroutineContext = Dispatchers.Default,
) : GetPreferredMediaSourceSortingUseCase {
    override fun invoke(): Flow<List<String>> {
        return mediaSourceManager.allInstances.map { list ->
            list.map {
                it.instanceId
            }
        }.flowOn(context)
    }
}
