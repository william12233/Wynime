package com.wynime.app.data.repository.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.subject.FollowedSubjectInfo
import com.wynime.app.data.models.subject.SubjectAiringInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.models.subject.hasNewEpisodeToPlay
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.session.SessionStateProvider
import com.wynime.app.domain.session.restartOnNewLogin
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

sealed interface FollowedSubjectsState {
    data object Loading : FollowedSubjectsState
    data class Content(val items: List<FollowedSubjectInfo>) : FollowedSubjectsState
    data class Error(val error: LoadError) : FollowedSubjectsState
}

class FollowedSubjectsRepository(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val sessionManager: SessionStateProvider,
    settingsRepository: SettingsRepository,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    private val nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

    fun followedSubjectsFlow(updatePeriod: Duration = 1.hours): Flow<FollowedSubjectsState> {
        require(updatePeriod > Duration.ZERO) { "updatePeriod must be positive" }

        val refresh = flow<FollowedSubjectsState> {
            emit(FollowedSubjectsState.Loading)
            while (true) {
                val result = try {
                    withTimeoutOrNull(15.seconds) {
                        subjectCollectionRepository.updateRecentlyUpdatedSubjectCollections(
                            30,
                            UnifiedCollectionType.DOING,
                        )
                    } ?: throw RepositoryNetworkException("Collection refresh timed out")
                    FollowedSubjectsState.Content(emptyList())
                } catch (e: Exception) {
                    val failure = RepositoryException.wrapOrThrowCancellation(e)
                    logger.error(failure) { "Could not refresh continue watching collections" }
                    FollowedSubjectsState.Error(LoadError.fromException(failure))
                }
                emit(result)
                delay(updatePeriod)
            }
        }

        val local = subjectCollectionRepository.mostRecentlyUpdatedSubjectCollectionsFlow(
            limit = 64,
            types = listOf(UnifiedCollectionType.DOING),
        )
        return combine(local, nsfwModeSettingsFlow, refresh) { subjects, nsfwMode, sync ->
            val now = PackedDate.now()
            val items = subjects.asSequence()
                .filter { !it.subjectInfo.nsfw || nsfwMode != NsfwMode.HIDE }
                .map { subject ->
                    val episodes = subject.episodes
                    FollowedSubjectInfo(
                        subject,
                        SubjectAiringInfo.computeFromEpisodeList(
                            episodes.map { it.episodeInfo },
                            subject.subjectInfo.airDate,
                            subject.recurrence,
                        ),
                        SubjectProgressInfo.compute(subject.subjectInfo, episodes, now, subject.recurrence),
                        nsfwMode = if (subject.subjectInfo.nsfw) nsfwMode else NsfwMode.DISPLAY,
                    )
                }
                .sortedWith(sorter)
                .toList()
            if (items.isEmpty()) sync else FollowedSubjectsState.Content(items)
        }.catch { e ->
            emit(FollowedSubjectsState.Error(LoadError.fromException(RepositoryException.wrapOrThrowCancellation(e))))
        }.restartOnNewLogin(sessionManager).distinctUntilChanged().flowOn(defaultDispatcher)
    }

    private companion object {
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

