package com.wynime.app.data.repository.subject

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.subject.*
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryUnknownException
import com.wynime.app.data.repository.episode.EpisodeCollectionRepository
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.domain.session.restartOnNewLogin
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class FollowedSubjectsRepository(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val episodeCollectionRepository: EpisodeCollectionRepository,

    private val sessionManager: SessionStateProvider,
    settingsRepository: SettingsRepository,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    private val nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

    private fun followedSubjectsFlow(
        updatePeriod: Duration = 1.hours,
    ): Flow<List<FollowedSubjectInfo>> {
        require(updatePeriod > Duration.ZERO) { "updatePeriod must be positive" }

        val ticker = flow {
            while (true) {
                emit(Unit)
                kotlinx.coroutines.delay(updatePeriod)
            }
        }

        val now = PackedDate.now()

        return ticker.flatMapLatest {
            try {
                subjectCollectionRepository.updateRecentlyUpdatedSubjectCollections(
                    30,
                    UnifiedCollectionType.DOING,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val displayE = when (e) {
                    is RepositoryUnknownException -> e
                    is RepositoryException -> null
                    else -> e
                }

                logger.error(displayE) { """Failed to update recently updated subject collections due to ${e}, ignoring. 这只会导致探索页的继续观看栏目可能显示旧结果. """ }
            }

            subjectCollectionRepository.mostRecentlyUpdatedSubjectCollectionsFlow(
                limit = 64,
                types = listOf(
                    UnifiedCollectionType.DOING,
                ),
            ).flatMapLatest { subjectCollectionInfoList ->

                if (subjectCollectionInfoList.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList())
                }
                getFollowedSubjectInfoFlows(subjectCollectionInfoList, now)
            }.map { followedSubjectInfoList ->
                followedSubjectInfoList
                    .toMutableList()
                    .apply {
                        sortWith(sorter)
                    }
            }.catch {
                throw RepositoryException.wrapOrThrowCancellation(it)
            }
        }.flowOn(defaultDispatcher)
    }

    private fun getFollowedSubjectInfoFlows(
        subjectCollectionInfoList: List<SubjectCollectionInfo>,
        now: PackedDate,
    ): Flow<List<FollowedSubjectInfo>> = combine(
        subjectCollectionInfoList.map { info ->
            episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(info.subjectId)
        },
    ) { array ->
        array.toList()
    }.combine(nsfwModeSettingsFlow) { epInfoLists, nsfwMode ->
        subjectCollectionInfoList.asSequence().zip(epInfoLists.asSequence()) { subjectCollectionInfo, episodes ->

            FollowedSubjectInfo(
                subjectCollectionInfo,
                SubjectAiringInfo.computeFromEpisodeList(
                    episodes.map { it.episodeInfo },
                    subjectCollectionInfo.subjectInfo.airDate,
                    subjectCollectionInfo.recurrence,
                ),
                SubjectProgressInfo.compute(
                    subjectCollectionInfo.subjectInfo,
                    episodes,
                    now,
                    subjectCollectionInfo.recurrence,
                ),
                nsfwMode =
                    if (subjectCollectionInfo.subjectInfo.nsfw) nsfwMode
                    else NsfwMode.DISPLAY,
            )
        }.toList()
    }.flowOn(defaultDispatcher)

    fun followedSubjectsPager(
        updatePeriod: Duration = 1.hours,
    ) = followedSubjectsFlow(updatePeriod)
        .restartOnNewLogin(sessionManager)
        .map {
            PagingData.from(
                it,
                NotLoading,
            )
        }.flowOn(defaultDispatcher)

    private companion object {
        private val NotLoading = LoadStates(
            refresh = LoadState.NotLoading(true),
            prepend = LoadState.NotLoading(true),
            append = LoadState.NotLoading(true),
        )

        val sorter: Comparator<FollowedSubjectInfo> =

            compareByDescending<FollowedSubjectInfo> { info ->

                info.subjectProgressInfo.hasNewEpisodeToPlay
            }.thenByDescending { info ->

                info.subjectCollectionInfo.collectionType == UnifiedCollectionType.DOING
            }.thenByDescending { info ->

                info.subjectCollectionInfo.lastUpdated
            }.thenByDescending { info ->

                val firstEp = info.subjectCollectionInfo.episodes.firstOrNull()?.episodeInfo?.sort
                val firstDone =
                    info.subjectCollectionInfo.episodes.firstOrNull { it.collectionType == UnifiedCollectionType.DONE }
                        ?.episodeInfo?.sort
                if (firstEp != null && firstDone != null) {
                    firstDone.compareTo(firstEp)
                } else {
                    Int.MIN_VALUE
                }
            }

    }
}

