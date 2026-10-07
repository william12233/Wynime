package com.wynime.app.data.network

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.recommend.RecommendedItemInfo
import com.wynime.app.data.models.recommend.RecommendedSubjectInfo
import com.wynime.app.data.models.trending.TrendingSubjectInfo
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.app.data.repository.runWrappingExceptionAsLoadResult
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.logging.error
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ln

class RecommendationRepository(
    private val dataSource: BangumiExploreDataSource,
    private val trendsRepository: TrendsRepository,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    private val calendarRepository: BangumiCalendarRepository = BangumiCalendarRepository(dataSource),
) : Repository() {
    fun recommendedSubjectsPager(): Flow<PagingData<RecommendedItemInfo>> {
        return Pager(defaultPagingConfig, initialKey = 0) {
            BangumiRecommendationPagingSource()
        }.flow
    }

    private inner class BangumiRecommendationPagingSource : PagingSource<Int, RecommendedItemInfo>() {
        override fun getRefreshKey(state: PagingState<Int, RecommendedItemInfo>): Int? = state.anchorPosition

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecommendedItemInfo> {
            return runWrappingExceptionAsLoadResult {
                val recommendations = withContext(ioDispatcher) { loadRecommendations() }
                val data: List<RecommendedItemInfo> = recommendations.map { it.toRecommendedSubjectInfo() }

                PagingSource.LoadResult.Page<Int, RecommendedItemInfo>(
                    data = data,
                    prevKey = null,
                    nextKey = null,
                )
            }.also {
                if (it is LoadResult.Error) {
                    logger.error(it.throwable) {
                        "Failed to load Bangumi recommendations (operation=trending plus local ranking)."
                    }
                }
            }
        }
    }

    private suspend fun loadRecommendations(): List<BangumiRecommendationCandidate> {
        val trending = trendsRepository.getTrendsInfo().subjects
        val calendar = try {
            calendarRepository.getCalendarDays().flatMap { it.items }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logger.error(e) {
                "Failed to load Bangumi calendar while building recommendations; continuing with trending candidates."
            }
            emptyList()
        }
        val preferences = try {
            dataSource.getCollectionPreferences()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logger.error(e) {
                "Failed to load local Bangumi collection preferences; continuing without personalization."
            }
            emptyList()
        }
        val nsfwMode = settingsRepository.uiSettings.flow.first().searchSettings.nsfwMode
        return rankBangumiRecommendations(
            candidates = mergeCandidates(trending, calendar),
            preferences = preferences,
            nsfwMode = nsfwMode,
            limit = RECOMMENDATION_LIMIT,
        )
    }

    private fun mergeCandidates(
        trending: List<TrendingSubjectInfo>,
        calendar: List<BangumiCalendarEntry>,
    ): List<BangumiRecommendationCandidate> {
        val merged = LinkedHashMap<Int, BangumiRecommendationCandidate>()
        trending.forEachIndexed { index, subject ->
            merged[subject.bangumiId] = BangumiRecommendationCandidate(
                id = subject.bangumiId,
                name = subject.name,
                nameCn = subject.nameCn,
                imageLarge = subject.imageLarge,
                nsfw = subject.nsfw,
                score = subject.score,
                scoreCount = subject.scoreCount,
                rank = subject.rank,
                tags = subject.tags,
                airDate = subject.airDate,
                trendingCount = subject.trendingCount,
                sourceOrder = index,
            )
        }
        calendar.forEach { entry ->
            val existing = merged[entry.id]
            if (existing == null) {
                merged[entry.id] = BangumiRecommendationCandidate(
                    id = entry.id,
                    name = entry.name,
                    nameCn = entry.nameCn,
                    imageLarge = entry.imageLarge,
                    nsfw = entry.nsfw,
                    airDate = entry.airDate,
                    sourceOrder = merged.size,
                )
            } else if (existing.airDate == null && entry.airDate != null) {
                merged[entry.id] = existing.copy(airDate = entry.airDate)
            }
        }
        return merged.values.toList()
    }

    private fun BangumiRecommendationCandidate.toRecommendedSubjectInfo(): RecommendedSubjectInfo {
        return RecommendedSubjectInfo(
            bangumiId = id,
            nameCn = nameCn,
            name = name,
            imageLarge = imageLarge,
            nsfw = nsfw,
            score = score,
            scoreCount = scoreCount,
            airDate = airDate,
        )
    }

    private companion object {
        const val RECOMMENDATION_LIMIT = 50
    }
}

internal data class BangumiRecommendationCandidate(
    val id: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
    val nsfw: Boolean = false,
    val score: Double = 0.0,
    val scoreCount: Int = 0,
    val rank: Int = 0,
    val tags: List<String> = emptyList(),
    val airDate: LocalDate? = null,
    val trendingCount: Int = 0,
    val sourceOrder: Int = 0,
)

internal fun rankBangumiRecommendations(
    candidates: List<BangumiRecommendationCandidate>,
    preferences: List<BangumiCollectionPreference>,
    nsfwMode: NsfwMode,
    limit: Int,
): List<BangumiRecommendationCandidate> {
    val preferenceById = preferences.associateBy { it.subjectId }
    val preferredTags = preferences
        .flatMap { it.tags + it.subjectTags }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    return candidates
        .asSequence()
        .filter { nsfwMode != NsfwMode.HIDE || !it.nsfw }
        .filterNot { preferenceById.containsKey(it.id) }
        .distinctBy { it.id }
        .map { candidate ->
            val tagOverlap = candidate.tags.count { it in preferredTags }
            val airingBonus = if (candidate.airDate != null) 4.0 else 0.0
            val ratingBonus = candidate.score.coerceIn(0.0, 10.0) * 2.0
            val ratingConfidence = ln((candidate.scoreCount + 1).toDouble()).coerceAtMost(8.0)
            val rankBonus = if (candidate.rank > 0) 2.0 / candidate.rank.coerceAtLeast(1) else 0.0
            val trendingBonus = ln((candidate.trendingCount + 1).toDouble())
            val personalizationBonus = tagOverlap * 5.0 + preferenceById[candidate.id]?.score.orZero() * 0.2
            ScoredBangumiRecommendation(
                candidate = candidate,
                score = ratingBonus + ratingConfidence + rankBonus + trendingBonus + airingBonus + personalizationBonus,
            )
        }
        .sortedWith(
            compareByDescending<ScoredBangumiRecommendation> { it.score }
                .thenBy { it.candidate.sourceOrder }
                .thenBy { it.candidate.id },
        )
        .take(limit.coerceAtLeast(0))
        .map { it.candidate }
        .toList()
}

private data class ScoredBangumiRecommendation(
    val candidate: BangumiRecommendationCandidate,
    val score: Double,
)

private fun Int?.orZero(): Int = this ?: 0

private fun throwIfCancellation(throwable: Throwable) {
    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
    RepositoryException.wrapOrThrowCancellation(throwable)
}
