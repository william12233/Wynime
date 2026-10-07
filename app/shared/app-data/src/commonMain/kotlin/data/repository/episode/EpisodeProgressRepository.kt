package com.wynime.app.data.repository.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.episode.EpisodeProgressInfo
import com.wynime.app.data.repository.Repository
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.utils.coroutines.flows.flowOfEmptyList
import kotlin.coroutines.CoroutineContext

class EpisodeProgressRepository(
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val downloadManager: MediaDownloadManager,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    fun subjectEpisodeProgressesInfoFlow(subjectId: Int): Flow<List<EpisodeProgressInfo>> {
        return episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId).flatMapLatest { list ->
            if (list.isEmpty()) {
                return@flatMapLatest flowOfEmptyList()
            }
            combine(
                list.map { info ->
                    downloadManager.downloadStatusForEpisode(subjectId, episodeId = info.episodeInfo.episodeId)
                        .map { cache ->
                            EpisodeProgressInfo(info.episodeInfo, info.collectionType, cache)
                        }
                },
            ) {
                it.toList()
            }
        }.flowOn(defaultDispatcher)
    }
}