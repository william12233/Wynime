package com.wynime.app.data.models.bangumi

import com.wynime.models.BangumiFullSyncStateDto
import com.wynime.models.BangumiSyncErrorDto
import com.wynime.models.BangumiSyncStateEntityDto

sealed interface BangumiSyncState {
    val finished: Boolean get() = false

    data object Preparing : BangumiSyncState

    data class FetchingSubjects(val fetchedCount: Int, val totalCount: Int? = null) : BangumiSyncState
    data class FetchingEpisodes(val fetchedCount: Int, val totalCount: Int? = null) : BangumiSyncState
    data class Inserting(val savedCount: Int, val totalCount: Int? = null) : BangumiSyncState

    data class Finishing(val savedCount: Int, val totalCount: Int? = null) : BangumiSyncState

    data class Finished(
        val savedCount: Int,
        val error: BangumiSyncErrorDto?,
        val localError: String? = null,
    ) : BangumiSyncState {
        override val finished: Boolean
            get() = true
    }

    data object Unsupported : BangumiSyncState

    companion object {
        fun fromEntity(entity: BangumiSyncStateEntityDto): BangumiSyncState? {
            return when (entity.state) {
                null -> Unsupported
                BangumiFullSyncStateDto.PREPARING -> Preparing
                BangumiFullSyncStateDto.FETCHING_SUBJECTS -> FetchingSubjects(entity.value ?: 0)
                BangumiFullSyncStateDto.FETCHING_EPISODES -> FetchingEpisodes(entity.value ?: 0)
                BangumiFullSyncStateDto.INSERTING_DATABASE -> Inserting(entity.value ?: 0)
                BangumiFullSyncStateDto.FINISHING -> Finishing(entity.value ?: 0)
                BangumiFullSyncStateDto.FINISHED -> Finished(entity.value ?: 0, entity.error)
            }
        }
    }
}
